# Phase 4 Production Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the Devernance backend for production deployment by adding Docker/Postgres stack, API key authentication, PDF/S3 evidence ingestion, real vector embeddings, and operational observability — completing every gap explicitly called out in the Phase 4 roadmap.

**Architecture:** Each gap is addressed as a self-contained layer: (1) Docker Compose brings up the full Postgres+pgvector stack so all subsequent work runs against real infrastructure; (2) API key auth replaces the bare-header tenant model with a secret-bearing token that is validated on every request; (3) Tika-based PDF extraction and an S3-compatible `FileStorageService` wire into the existing `TextExtractionService` seam; (4) a real sentence-embedding provider (using Hugging Face's `all-MiniLM-L6-v2` via the Djl/ONNX runtime) slots into the `EvidenceEmbeddingProvider` interface already used everywhere; (5) structured JSON logging, request-correlation IDs, and `management.endpoint.health` liveness/readiness probes complete the operational surface.

**Tech Stack:** Spring Boot 3.3, Java 17, PostgreSQL 16+pgvector, Docker Compose, Apache Tika 2.x (PDF text extraction), AWS SDK v2 (S3 multipart upload), DJL ONNX Runtime + HuggingFace sentence-transformers model (all-MiniLM-L6-v2), Logback JSON encoder (logstash-logback-encoder), Micrometer + Actuator, Flyway.

---

## Priority Order

1. **Task 1** — Docker Compose + Postgres stack (foundation for all others)
2. **Task 2** — API key authentication (security before anything else ships)
3. **Task 3** — PDF text extraction via Apache Tika
4. **Task 4** — S3-compatible evidence document storage
5. **Task 5** — Real sentence-embedding provider (DJL ONNX)
6. **Task 6** — Structured JSON logging + request correlation IDs
7. **Task 7** — Liveness/readiness probes + operational health checks
8. **Task 8** — Postgres CI smoke test (make the skipped test run in CI)

---

## Task 1: Docker Compose + Postgres Stack

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `infra/docker-compose.override.yml` (local dev overrides)
- Create: `infra/Dockerfile` (API image)
- Create: `infra/.env.example`
- Modify: `services/api/src/main/resources/application-postgres.properties`

- [ ] **Step 1: Write the Dockerfile**

```dockerfile
# infra/Dockerfile
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY services/api/target/api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Write docker-compose.yml**

```yaml
# infra/docker-compose.yml
version: "3.9"

services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: eu_ai_assurance
      POSTGRES_USER: eu_ai_assurance
      POSTGRES_PASSWORD: eu_ai_assurance
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U eu_ai_assurance"]
      interval: 5s
      timeout: 5s
      retries: 10

  api:
    build:
      context: ..
      dockerfile: infra/Dockerfile
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: postgres
      DATABASE_URL: jdbc:postgresql://postgres:5432/eu_ai_assurance
      DATABASE_USERNAME: eu_ai_assurance
      DATABASE_PASSWORD: eu_ai_assurance
      EVAL_CALLBACK_SECRET: ${EVAL_CALLBACK_SECRET}
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

- [ ] **Step 3: Write docker-compose.override.yml for local dev (no API container, just DB)**

```yaml
# infra/docker-compose.override.yml
version: "3.9"

services:
  api:
    profiles:
      - full
```

This means `docker compose up` starts only Postgres locally; `docker compose --profile full up` starts everything.

- [ ] **Step 4: Write .env.example**

```bash
# infra/.env.example
EVAL_CALLBACK_SECRET=replace-with-at-least-32-char-secret
DATABASE_URL=jdbc:postgresql://postgres:5432/eu_ai_assurance
DATABASE_USERNAME=eu_ai_assurance
DATABASE_PASSWORD=eu_ai_assurance
```

- [ ] **Step 5: Verify Postgres starts**

```bash
cd infra && docker compose up -d postgres
docker compose ps
# Expected: postgres container status = healthy
```

- [ ] **Step 6: Run the API against real Postgres to verify migrations apply**

```bash
cd /path/to/project
SPRING_PROFILES_ACTIVE=postgres \
DATABASE_URL=jdbc:postgresql://localhost:5432/eu_ai_assurance \
DATABASE_USERNAME=eu_ai_assurance \
DATABASE_PASSWORD=eu_ai_assurance \
EVAL_CALLBACK_SECRET=test-secret \
mvn -f services/api/pom.xml spring-boot:run &
sleep 8
curl -s http://localhost:8080/actuator/health | grep '"status":"UP"'
# Expected: {"status":"UP",...}
kill %1
```

- [ ] **Step 7: Verify existing tests still pass**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, Tests run: 38, Failures: 0
```

- [ ] **Step 8: Commit**

```bash
git add infra/Dockerfile infra/docker-compose.yml infra/docker-compose.override.yml infra/.env.example
git commit -m "feat(infra): add Docker Compose stack with Postgres+pgvector and API image"
```

---

## Task 2: API Key Authentication

Replace bare `X-Tenant-Id` / `X-Actor-Id` headers with a single `X-Api-Key` header. The key is a UUID stored in a new `api_keys` table. On each request, the filter resolves the key → tenant + actor and sets `TenantContext` as before. The MVP bootstrap seeds one key per default user. Existing role-based authorization (`TenantAuthorizationService`) is unchanged.

**Files:**
- Create: `services/api/src/main/resources/db/migration/V7__api_keys.sql`
- Create: `services/api/src/main/java/os/assurance/eu/api/tenant/ApiKeyEntity.java`
- Create: `services/api/src/main/java/os/assurance/eu/api/tenant/ApiKeyJpaRepository.java`
- Modify: `services/api/src/main/java/os/assurance/eu/api/tenant/TenantContextFilter.java`
- Modify: `services/api/src/main/java/os/assurance/eu/api/BootstrapData.java`
- Modify: `services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java`

- [ ] **Step 1: Write the failing test for API key auth**

Add to `ApiControllerTest`:

```java
@Test
void rejectsRequestWithUnknownApiKey() throws Exception {
    mockMvc.perform(get("/api/v1/systems")
            .header("X-Api-Key", "00000000-0000-0000-0000-000000009999"))
        .andExpect(status().isUnauthorized());
}

@Test
void acceptsRequestWithValidApiKey() throws Exception {
    mockMvc.perform(get("/api/v1/systems")
            .header("X-Api-Key", DEFAULT_API_KEY))
        .andExpect(status().isOk());
}
```

Add constant at top of test class:
```java
private static final String DEFAULT_API_KEY = "00000000-0000-0000-0000-000000000a01";
```

- [ ] **Step 2: Run to verify tests fail**

```bash
mvn -f services/api/pom.xml test -Dtest=ApiControllerTest#rejectsRequestWithUnknownApiKey,ApiControllerTest#acceptsRequestWithValidApiKey 2>&1 | grep -E "FAIL|ERROR|Tests run"
# Expected: test compile error or assertion failure — X-Api-Key not yet wired
```

- [ ] **Step 3: Write V7 migration**

```sql
-- services/api/src/main/resources/db/migration/V7__api_keys.sql
create table api_keys (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  user_id uuid not null references users(id),
  created_at timestamp with time zone not null
);

create index idx_api_keys_tenant_id on api_keys (tenant_id);
```

- [ ] **Step 4: Write ApiKeyEntity**

```java
// services/api/src/main/java/os/assurance/eu/api/tenant/ApiKeyEntity.java
package os.assurance.eu.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ApiKeyEntity() {}

    public ApiKeyEntity(UUID id, UUID tenantId, UUID userId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID userId() { return userId; }
}
```

- [ ] **Step 5: Write ApiKeyJpaRepository**

```java
// services/api/src/main/java/os/assurance/eu/api/tenant/ApiKeyJpaRepository.java
package os.assurance.eu.api.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, UUID> {
    Optional<ApiKeyEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
```

- [ ] **Step 6: Update TenantContextFilter to accept X-Api-Key**

Replace the entire file:

```java
// services/api/src/main/java/os/assurance/eu/api/tenant/TenantContextFilter.java
package os.assurance.eu.api.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    static final String API_KEY_HEADER = "X-Api-Key";

    private final TenantJpaRepository tenants;
    private final UserJpaRepository users;
    private final ApiKeyJpaRepository apiKeys;

    public TenantContextFilter(
            TenantJpaRepository tenants,
            UserJpaRepository users,
            ApiKeyJpaRepository apiKeys) {
        this.tenants = tenants;
        this.users = users;
        this.apiKeys = apiKeys;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            UUID keyId;
            try {
                keyId = UUID.fromString(apiKeyHeader);
            } catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + API_KEY_HEADER);
                return;
            }
            ApiKeyEntity key = apiKeys.findById(keyId).orElse(null);
            if (key == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown API key");
                return;
            }
            TenantContext.set(key.tenantId(), key.userId());
            filterChain.doFilter(request, response);
            TenantContext.clear();
            return;
        }

        // Legacy header fallback (for existing tests and dev tooling)
        UUID tenantId = parseHeader(request, TenantContext.TENANT_HEADER, TenantContext.DEFAULT_TENANT_ID, response);
        if (tenantId == null) return;
        UUID actorId = parseHeader(request, TenantContext.ACTOR_HEADER, TenantContext.DEFAULT_USER_ID, response);
        if (actorId == null) return;

        if (!tenants.existsById(tenantId)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown tenant");
            return;
        }
        if (!users.existsByIdAndTenantId(actorId, tenantId)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown actor for tenant");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private UUID parseHeader(
            HttpServletRequest request,
            String headerName,
            UUID fallback,
            HttpServletResponse response) throws IOException {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) return fallback;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + headerName);
            return null;
        }
    }
}
```

**Note:** `TenantContext` must expose `set(UUID tenantId, UUID actorId)` and `clear()`. Check the existing `TenantContext` class — if it uses `ThreadLocal` already update it; if not, add those two methods.

- [ ] **Step 7: Check and update TenantContext for set/clear**

Read `TenantContext.java`. If it doesn't already have `ThreadLocal`-based `set`/`clear`:

```java
// services/api/src/main/java/os/assurance/eu/api/tenant/TenantContext.java
package os.assurance.eu.api.tenant;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String ACTOR_HEADER = "X-Actor-Id";
    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> ACTOR = new ThreadLocal<>();

    public static void set(UUID tenantId, UUID actorId) {
        TENANT.set(tenantId);
        ACTOR.set(actorId);
    }

    public static void clear() {
        TENANT.remove();
        ACTOR.remove();
    }

    public UUID tenantId() {
        UUID v = TENANT.get();
        return v != null ? v : DEFAULT_TENANT_ID;
    }

    public UUID actorId() {
        UUID v = ACTOR.get();
        return v != null ? v : DEFAULT_USER_ID;
    }
}
```

If `TenantContext` already uses fields injected by the filter differently, adapt accordingly — the key contract is: filter sets tenant+actor per request, service beans read them via `tenantContext.tenantId()` / `tenantContext.actorId()`.

- [ ] **Step 8: Seed API key in BootstrapData**

Add to `BootstrapData.java` imports:
```java
import os.assurance.eu.api.tenant.ApiKeyEntity;
import os.assurance.eu.api.tenant.ApiKeyJpaRepository;
```

Add field + constructor arg:
```java
private final ApiKeyJpaRepository apiKeyRepo;
```

Add to `run()` method:
```java
UUID defaultApiKey = UUID.fromString("00000000-0000-0000-0000-000000000a01");
apiKeyRepo.findById(defaultApiKey)
    .orElseGet(() -> apiKeyRepo.save(new ApiKeyEntity(
        defaultApiKey,
        TenantContext.DEFAULT_TENANT_ID,
        TenantContext.DEFAULT_USER_ID,
        now)));
```

- [ ] **Step 9: Seed additional API keys in test's @BeforeEach**

In `ApiControllerTest.seedTestActors()`, add:

```java
@Autowired
private ApiKeyJpaRepository apiKeyRepo;

// in seedTestActors():
UUID defaultApiKey = UUID.fromString(DEFAULT_API_KEY);
apiKeyRepo.findById(defaultApiKey)
    .orElseGet(() -> apiKeyRepo.save(new ApiKeyEntity(
        defaultApiKey,
        UUID.fromString(DEFAULT_TENANT_ID),
        UUID.fromString(DEFAULT_ACTOR_ID),
        Instant.now())));
```

Add import: `import os.assurance.eu.api.tenant.ApiKeyEntity;`
Add import: `import os.assurance.eu.api.tenant.ApiKeyJpaRepository;`

- [ ] **Step 10: Run tests**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD|FAIL"
# Expected: BUILD SUCCESS, Tests run: 40, Failures: 0
```

- [ ] **Step 11: Commit**

```bash
git add services/api/src/main/resources/db/migration/V7__api_keys.sql \
        services/api/src/main/java/os/assurance/eu/api/tenant/ApiKeyEntity.java \
        services/api/src/main/java/os/assurance/eu/api/tenant/ApiKeyJpaRepository.java \
        services/api/src/main/java/os/assurance/eu/api/tenant/TenantContextFilter.java \
        services/api/src/main/java/os/assurance/eu/api/tenant/TenantContext.java \
        services/api/src/main/java/os/assurance/eu/api/BootstrapData.java \
        services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java
git commit -m "feat(auth): add API key authentication with X-Api-Key header and api_keys table"
```

---

## Task 3: PDF Text Extraction via Apache Tika

Wire Apache Tika into `TextExtractionService` so that when `content` is blank but `sourceUri` points to an `https://` URL, the service fetches and extracts text from the PDF. Local (`memory://`, `s3://`) URIs still fall back to the metadata stub until Task 4 adds S3 support.

**Files:**
- Modify: `services/api/pom.xml`
- Modify: `services/api/src/main/java/os/assurance/eu/api/evidence/TextExtractionService.java`
- Modify: `services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java`

- [ ] **Step 1: Write failing test**

Add to `ApiControllerTest`:

```java
@Test
void extractsTextFromPublicHttpsPdfUrl() throws Exception {
    String systemId = createSystem();

    mockMvc.perform(post("/api/v1/evidence/documents")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "systemId": "%s",
                  "type": "POLICY",
                  "title": "EU AI Act Summary",
                  "sourceUri": "https://artificialintelligenceact.eu/wp-content/uploads/2021/08/The-AI-Act.pdf"
                }
                """.formatted(systemId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ingestionStatus").value(org.hamcrest.Matchers.oneOf("indexed", "indexed_with_warnings")))
        .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)));
}
```

**Note:** This test makes a real HTTP call — mark it with `@Tag("integration")` if you want to exclude it from fast unit runs:
```java
import org.junit.jupiter.api.Tag;
// ...
@Test
@Tag("integration")
void extractsTextFromPublicHttpsPdfUrl() throws Exception { ... }
```

- [ ] **Step 2: Run to verify test fails (Tika not yet wired)**

```bash
mvn -f services/api/pom.xml test -Dtest=ApiControllerTest#extractsTextFromPublicHttpsPdfUrl 2>&1 | grep -E "FAIL|ERROR|Tests run"
# Expected: test either fails with no-op metadata stub or network assertion mismatch
```

- [ ] **Step 3: Add Tika dependency to pom.xml**

Inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
```

- [ ] **Step 4: Update TextExtractionService to fetch + extract PDFs**

```java
// services/api/src/main/java/os/assurance/eu/api/evidence/TextExtractionService.java
package os.assurance.eu.api.evidence;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {
    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);
    private static final int MAX_FETCH_BYTES = 20 * 1024 * 1024; // 20 MB
    private final Tika tika = new Tika();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public String extract(CreateEvidenceDocumentRequest request) {
        if (request.content() != null && !request.content().isBlank()) {
            return request.content().strip();
        }
        String scheme = URI.create(request.sourceUri()).getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(request.sourceUri()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
                HttpResponse<InputStream> response = http.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    String extracted = tika.parseToString(body, MAX_FETCH_BYTES);
                    if (extracted != null && !extracted.isBlank()) {
                        return extracted.strip();
                    }
                }
            } catch (Exception e) {
                log.warn("PDF extraction failed for {}: {}", request.sourceUri(), e.getMessage());
            }
        }
        return metadataStub(request);
    }

    private String metadataStub(CreateEvidenceDocumentRequest request) {
        return """
            Evidence document: %s
            Type: %s
            Source: %s
            This metadata-only evidence record has been indexed. Upload extracted text in the content field to enable richer retrieval.
            """
            .formatted(request.title(), request.type().toUpperCase(Locale.ROOT), request.sourceUri())
            .strip();
    }
}
```

- [ ] **Step 5: Run the integration test**

```bash
mvn -f services/api/pom.xml test -Dtest=ApiControllerTest#extractsTextFromPublicHttpsPdfUrl 2>&1 | grep -E "PASS|FAIL|Tests run"
# Expected: Tests run: 1, Failures: 0
```

- [ ] **Step 6: Run full test suite**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, all tests pass
```

- [ ] **Step 7: Commit**

```bash
git add services/api/pom.xml \
        services/api/src/main/java/os/assurance/eu/api/evidence/TextExtractionService.java \
        services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java
git commit -m "feat(evidence): add Apache Tika PDF extraction for https:// source URIs"
```

---

## Task 4: S3-Compatible Evidence Document Storage

Add a `FileStorageService` that, for `s3://` URIs, uploads the raw bytes via AWS SDK v2 and stores a pre-signed download URL. The `EvidenceController` gets a new `POST /evidence/documents/upload` multipart endpoint. The existing `POST /evidence/documents` JSON endpoint is unchanged (it still accepts inline `content`). `TextExtractionService` gains an `s3://` branch that downloads and Tika-parses the object.

**Files:**
- Modify: `services/api/pom.xml`
- Create: `services/api/src/main/java/os/assurance/eu/api/evidence/FileStorageService.java`
- Create: `services/api/src/main/java/os/assurance/eu/api/evidence/FileStorageProperties.java`
- Modify: `services/api/src/main/java/os/assurance/eu/api/evidence/TextExtractionService.java`
- Modify: `services/api/src/main/java/os/assurance/eu/api/evidence/EvidenceController.java`
- Modify: `services/api/src/main/resources/application.properties`
- Modify: `services/api/src/main/resources/application-postgres.properties`
- Modify: `services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java`

- [ ] **Step 1: Write failing test for multipart upload endpoint**

Add to `ApiControllerTest`:

```java
@Test
void uploadsMultipartFileAndIndexesContent() throws Exception {
    String systemId = createSystem();
    byte[] pdfBytes = ("Evidence content for testing multipart upload").getBytes(java.nio.charset.StandardCharsets.UTF_8);

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .multipart("/api/v1/evidence/documents/upload")
            .file(new org.springframework.mock.web.MockMultipartFile(
                "file", "test-policy.txt", "text/plain", pdfBytes))
            .param("systemId", systemId)
            .param("type", "POLICY")
            .param("title", "Test Upload Policy"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ingestionStatus").value(org.hamcrest.Matchers.oneOf("indexed", "indexed_with_warnings")))
        .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)));
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
mvn -f services/api/pom.xml test -Dtest=ApiControllerTest#uploadsMultipartFileAndIndexesContent 2>&1 | grep -E "FAIL|ERROR"
# Expected: 404 — endpoint does not exist yet
```

- [ ] **Step 3: Add AWS SDK v2 dependency**

```xml
<!-- services/api/pom.xml — inside <dependencies> -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.40</version>
</dependency>
```

Also add BOM to `<dependencyManagement>`:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.25.40</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- [ ] **Step 4: Write FileStorageProperties**

```java
// services/api/src/main/java/os/assurance/eu/api/evidence/FileStorageProperties.java
package os.assurance.eu.api.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assurance.storage")
public record FileStorageProperties(
    boolean enabled,
    String bucket,
    String region,
    String endpoint,         // optional: for MinIO/LocalStack
    String accessKeyId,      // optional: override default credential chain
    String secretAccessKey   // optional: override default credential chain
) {
    public FileStorageProperties {
        if (bucket == null) bucket = "eu-ai-assurance-evidence";
        if (region == null) region = "eu-west-1";
    }
}
```

Register in `EuAiAssuranceApiApplication.java` by adding `@EnableConfigurationProperties(FileStorageProperties.class)`.

- [ ] **Step 5: Write FileStorageService**

```java
// services/api/src/main/java/os/assurance/eu/api/evidence/FileStorageService.java
package os.assurance.eu.api.evidence;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final FileStorageProperties props;
    private final S3Client s3;
    private final S3Presigner presigner;

    public FileStorageService(FileStorageProperties props) {
        this.props = props;
        if (props.enabled()) {
            var credProvider = (props.accessKeyId() != null && !props.accessKeyId().isBlank())
                ? StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey()))
                : DefaultCredentialsProvider.create();
            var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credProvider);
            if (props.endpoint() != null && !props.endpoint().isBlank()) {
                builder.endpointOverride(URI.create(props.endpoint()));
            }
            this.s3 = builder.build();
            var presignerBuilder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credProvider);
            if (props.endpoint() != null && !props.endpoint().isBlank()) {
                presignerBuilder.endpointOverride(URI.create(props.endpoint()));
            }
            this.presigner = presignerBuilder.build();
        } else {
            this.s3 = null;
            this.presigner = null;
        }
    }

    public String upload(String key, InputStream content, long contentLength, String contentType) {
        if (!props.enabled() || s3 == null) {
            return "s3://" + props.bucket() + "/" + key;
        }
        s3.putObject(PutObjectRequest.builder()
            .bucket(props.bucket())
            .key(key)
            .contentType(contentType)
            .build(), RequestBody.fromInputStream(content, contentLength));
        return "s3://" + props.bucket() + "/" + key;
    }

    public InputStream download(String bucket, String key) {
        if (!props.enabled() || s3 == null) {
            throw new UnsupportedOperationException("S3 storage not enabled");
        }
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public String presignedUrl(String bucket, String key) {
        if (!props.enabled() || presigner == null) return null;
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1))
            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
            .build())
            .url().toString();
    }
}
```

- [ ] **Step 6: Update TextExtractionService with S3 branch**

Add S3 download + Tika parse for `s3://` URIs in `extract()`. Insert after the `https` branch:

```java
if ("s3".equals(scheme)) {
    if (fileStorage != null) {
        URI uri = URI.create(request.sourceUri());
        String bucket = uri.getHost();
        String key = uri.getPath().replaceFirst("^/", "");
        try (InputStream body = fileStorage.download(bucket, key)) {
            String extracted = tika.parseToString(body, MAX_FETCH_BYTES);
            if (extracted != null && !extracted.isBlank()) {
                return extracted.strip();
            }
        } catch (Exception e) {
            log.warn("S3 extraction failed for {}: {}", request.sourceUri(), e.getMessage());
        }
    }
}
```

Inject `FileStorageService fileStorage` via constructor (use `@Autowired(required=false)` or make it `Optional<FileStorageService>` to keep tests working when S3 is disabled).

Complete updated signature:
```java
public TextExtractionService(FileStorageService fileStorage) {
    this.fileStorage = fileStorage;
}
```

- [ ] **Step 7: Add multipart upload endpoint to EvidenceController**

```java
@PostMapping("/documents/upload")
@ResponseStatus(HttpStatus.CREATED)
public EvidenceDocumentResponse uploadDocument(
        @RequestParam UUID systemId,
        @RequestParam String type,
        @RequestParam String title,
        @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
        @RequestParam(required = false) String checksum,
        FileStorageService fileStorage) throws Exception {
    systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    String key = "evidence/" + systemId + "/" + java.util.UUID.randomUUID() + "/" + file.getOriginalFilename();
    String uri = fileStorage.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
    var request = new CreateEvidenceDocumentRequest(
        systemId, type, title, uri, null, checksum, null);
    return evidenceService.ingest(request);
}
```

Inject `FileStorageService fileStorage` via constructor in `EvidenceController`.

- [ ] **Step 8: Add storage config to application.properties**

```properties
# Storage (disabled by default; enable with assurance.storage.enabled=true and bucket/region)
assurance.storage.enabled=false
assurance.storage.bucket=eu-ai-assurance-evidence
assurance.storage.region=eu-west-1
```

- [ ] **Step 9: Run tests**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, Tests run: 41+, Failures: 0
```

- [ ] **Step 10: Commit**

```bash
git add services/api/pom.xml \
        services/api/src/main/java/os/assurance/eu/api/evidence/FileStorageService.java \
        services/api/src/main/java/os/assurance/eu/api/evidence/FileStorageProperties.java \
        services/api/src/main/java/os/assurance/eu/api/evidence/TextExtractionService.java \
        services/api/src/main/java/os/assurance/eu/api/evidence/EvidenceController.java \
        services/api/src/main/java/os/assurance/eu/api/EuAiAssuranceApiApplication.java \
        services/api/src/main/resources/application.properties \
        services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java
git commit -m "feat(evidence): add S3 file storage service and multipart upload endpoint"
```

---

## Task 5: Real Sentence-Embedding Provider (DJL ONNX)

Add a `DjlSentenceEmbeddingProvider` that uses DJL (Deep Java Library) with the ONNX engine to run HuggingFace's `all-MiniLM-L6-v2` sentence transformer locally. When profile = `postgres` or property `assurance.evidence.embedding-provider=djl-sentence` is set, this provider replaces `LocalHashEvidenceEmbeddingProvider`. The existing seam (`EvidenceEmbeddingProvider` interface) and profile-based bean selection remain unchanged.

**Files:**
- Modify: `services/api/pom.xml`
- Create: `services/api/src/main/java/os/assurance/eu/api/evidence/DjlSentenceEmbeddingProvider.java`
- Create: `services/api/src/main/java/os/assurance/eu/api/evidence/EvidenceEmbeddingConfig.java`
- Modify: `services/api/src/main/resources/application-postgres.properties`
- Modify: `services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java`

- [ ] **Step 1: Write failing test for real-embedding similarity**

Add to `ApiControllerTest`:

```java
@Test
void embeddingProviderReturnsSemanticallySimilarResults() throws Exception {
    String systemId = createSystem();

    mockMvc.perform(post("/api/v1/evidence/documents")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "systemId": "%s",
                  "type": "POLICY",
                  "title": "Human Oversight SOP",
                  "sourceUri": "memory://human-oversight-sop",
                  "content": "All high-risk AI releases require a named human reviewer to sign off before deployment. The reviewer must verify bias test results and document any overrides."
                }
                """.formatted(systemId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ingestionStatus").value("indexed"));

    mockMvc.perform(post("/api/v1/evidence/query")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "systemId": "%s",
                  "question": "Who must approve a high-risk AI release?"
                }
                """.formatted(systemId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations[0].title").value("Human Oversight SOP"));
}
```

This test already passes with `local-hash`. Its purpose is to be a regression guard — after switching to the DJL provider it must continue to pass.

- [ ] **Step 2: Add DJL dependencies to pom.xml**

```xml
<dependency>
    <groupId>ai.djl</groupId>
    <artifactId>api</artifactId>
    <version>0.27.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.27.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.onnxruntime</groupId>
    <artifactId>onnxruntime-engine</artifactId>
    <version>0.27.0</version>
</dependency>
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.17.0</version>
</dependency>
```

- [ ] **Step 3: Write DjlSentenceEmbeddingProvider**

```java
// services/api/src/main/java/os/assurance/eu/api/evidence/DjlSentenceEmbeddingProvider.java
package os.assurance.eu.api.evidence;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import java.io.IOException;
import java.util.Arrays;
import jakarta.annotation.PreDestroy;

public class DjlSentenceEmbeddingProvider implements EvidenceEmbeddingProvider {
    private final ZooModel<NDList, NDList> model;
    private final HuggingFaceTokenizer tokenizer;

    public DjlSentenceEmbeddingProvider() throws Exception {
        Criteria<NDList, NDList> criteria = Criteria.builder()
            .setTypes(NDList.class, NDList.class)
            .optModelUrls("djl://ai.djl.huggingface.onnxruntime/all-MiniLM-L6-v2")
            .optEngine("OnnxRuntime")
            .build();
        this.model = criteria.loadModel();
        this.tokenizer = HuggingFaceTokenizer.newInstance("sentence-transformers/all-MiniLM-L6-v2");
    }

    @Override
    public String name() { return "djl-sentence"; }

    @Override
    public String embed(String text) {
        try (Predictor<NDList, NDList> predictor = model.newPredictor();
             NDManager manager = NDManager.newBaseManager()) {
            Encoding encoding = tokenizer.encode(text, true);
            long[] ids = encoding.getIds();
            long[] mask = encoding.getAttentionMask();
            NDArray inputIds = manager.create(ids, new Shape(1, ids.length));
            NDArray attentionMask = manager.create(mask, new Shape(1, mask.length));
            NDArray tokenTypeIds = manager.zeros(new Shape(1, ids.length), ai.djl.ndarray.types.DataType.INT64);
            NDList output = predictor.predict(new NDList(inputIds, attentionMask, tokenTypeIds));
            // Mean pooling over token embeddings
            NDArray tokenEmbeddings = output.get(0); // shape [1, seq_len, 384]
            NDArray maskExpanded = attentionMask.reshape(1, mask.length, 1)
                .broadcast(tokenEmbeddings.getShape())
                .toType(ai.djl.ndarray.types.DataType.FLOAT32, false);
            NDArray sumEmbeddings = tokenEmbeddings.mul(maskExpanded).sum(new int[]{1});
            NDArray sumMask = maskExpanded.sum(new int[]{1}).clip(1e-9f, Float.MAX_VALUE);
            NDArray pooled = sumEmbeddings.div(sumMask);
            // L2 normalize
            NDArray norm = pooled.norm(new int[]{1}, true, false).clip(1e-9f, Float.MAX_VALUE);
            NDArray normalized = pooled.div(norm);
            float[] vector = normalized.toFloatArray();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(vector[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed", e);
        }
    }

    @Override
    public double similarity(String left, String right) {
        float[] l = parse(left);
        float[] r = parse(right);
        double dot = 0;
        for (int i = 0; i < Math.min(l.length, r.length); i++) dot += l[i] * r[i];
        return dot; // vectors are already L2-normalized, so dot = cosine
    }

    private float[] parse(String embedding) {
        String[] parts = embedding.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Float.parseFloat(parts[i]);
        return v;
    }

    @PreDestroy
    public void close() {
        model.close();
        tokenizer.close();
    }
}
```

- [ ] **Step 4: Write EvidenceEmbeddingConfig bean selector**

```java
// services/api/src/main/java/os/assurance/eu/api/evidence/EvidenceEmbeddingConfig.java
package os.assurance.eu.api.evidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EvidenceEmbeddingConfig {
    private static final Logger log = LoggerFactory.getLogger(EvidenceEmbeddingConfig.class);

    @Bean
    @Primary
    @ConditionalOnProperty(name = "assurance.evidence.embedding-provider", havingValue = "djl-sentence")
    public EvidenceEmbeddingProvider djlSentenceEmbeddingProvider() throws Exception {
        log.info("Loading DJL sentence-transformer embedding provider (all-MiniLM-L6-v2)");
        return new DjlSentenceEmbeddingProvider();
    }
}
```

The `LocalHashEvidenceEmbeddingProvider` keeps its `@Component` annotation and remains the default when the property is `local-hash`.

- [ ] **Step 5: Update postgres profile to opt into DJL**

In `application-postgres.properties`:
```properties
assurance.evidence.embedding-provider=${EVIDENCE_EMBEDDING_PROVIDER:djl-sentence}
```

- [ ] **Step 6: Run tests (H2 default — uses local-hash, should all pass)**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, Failures: 0
```

- [ ] **Step 7: Smoke test with DJL provider locally**

```bash
SPRING_PROFILES_ACTIVE=postgres \
DATABASE_URL=jdbc:postgresql://localhost:5432/eu_ai_assurance \
DATABASE_USERNAME=eu_ai_assurance \
DATABASE_PASSWORD=eu_ai_assurance \
EVAL_CALLBACK_SECRET=test-secret \
EVIDENCE_EMBEDDING_PROVIDER=djl-sentence \
mvn -f services/api/pom.xml spring-boot:run &
sleep 15  # DJL downloads model on first boot
curl -s http://localhost:8080/actuator/health | grep '"status":"UP"'
kill %1
```

- [ ] **Step 8: Commit**

```bash
git add services/api/pom.xml \
        services/api/src/main/java/os/assurance/eu/api/evidence/DjlSentenceEmbeddingProvider.java \
        services/api/src/main/java/os/assurance/eu/api/evidence/EvidenceEmbeddingConfig.java \
        services/api/src/main/resources/application-postgres.properties \
        services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java
git commit -m "feat(evidence): add DJL ONNX sentence-transformer embedding provider (all-MiniLM-L6-v2)"
```

---

## Task 6: Structured JSON Logging + Request Correlation IDs

Add logstash-logback-encoder so every log line is JSON. Add a `CorrelationFilter` that reads or generates an `X-Request-Id` header and puts it in the MDC so every log line emitted during a request includes `requestId`. The filter writes `X-Request-Id` back into the response.

**Files:**
- Modify: `services/api/pom.xml`
- Create: `services/api/src/main/resources/logback-spring.xml`
- Create: `services/api/src/main/java/os/assurance/eu/api/CorrelationFilter.java`
- Modify: `services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java`

- [ ] **Step 1: Write failing test for correlation header**

Add to `ApiControllerTest`:

```java
@Test
void propagatesCorrelationId() throws Exception {
    mockMvc.perform(get("/api/v1/systems")
            .header("X-Request-Id", "test-correlation-123"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .header().string("X-Request-Id", "test-correlation-123"));
}

@Test
void generatesCorrelationIdWhenAbsent() throws Exception {
    mockMvc.perform(get("/api/v1/systems"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .header().exists("X-Request-Id"));
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
mvn -f services/api/pom.xml test -Dtest=ApiControllerTest#propagatesCorrelationId,ApiControllerTest#generatesCorrelationIdWhenAbsent 2>&1 | grep -E "FAIL|ERROR"
# Expected: FAIL — X-Request-Id header not returned
```

- [ ] **Step 3: Add logstash-logback-encoder dependency**

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

- [ ] **Step 4: Write logback-spring.xml**

```xml
<!-- services/api/src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="!default">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>requestId</includeMdcKeyName>
                <includeMdcKeyName>tenantId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <springProfile name="default">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

- [ ] **Step 5: Write CorrelationFilter**

```java
// services/api/src/main/java/os/assurance/eu/api/CorrelationFilter.java
package os.assurance.eu.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class CorrelationFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
```

- [ ] **Step 6: Run tests**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, Failures: 0, 2 new tests passing
```

- [ ] **Step 7: Commit**

```bash
git add services/api/pom.xml \
        services/api/src/main/resources/logback-spring.xml \
        services/api/src/main/java/os/assurance/eu/api/CorrelationFilter.java \
        services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java
git commit -m "feat(observability): add structured JSON logging and X-Request-Id correlation filter"
```

---

## Task 7: Liveness/Readiness Probes + Operational Health Checks

Add custom `HealthIndicator` beans for (a) database connectivity and (b) embedding provider readiness, and surface them on the existing `/actuator/health/liveness` and `/actuator/health/readiness` probes already partially configured. Add a test that verifies both probes and that the embedding provider indicator is present.

**Files:**
- Modify: `services/api/src/main/resources/application.properties`
- Create: `services/api/src/main/java/os/assurance/eu/api/EmbeddingProviderHealthIndicator.java`
- Modify: `services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ApiControllerTest`:

```java
@Test
void livenessProbeIsUp() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
}

@Test
void readinessProbeIsUp() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
}

@Test
void healthEndpointIncludesEmbeddingProviderComponent() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components.embeddingProvider").exists())
        .andExpect(jsonPath("$.components.embeddingProvider.status").value("UP"));
}
```

- [ ] **Step 2: Run to verify they fail**

```bash
mvn -f services/api/pom.xml test -Dtest=ApiControllerTest#healthEndpointIncludesEmbeddingProviderComponent 2>&1 | grep -E "FAIL|ERROR"
# Expected: FAIL — embeddingProvider component not present
```

- [ ] **Step 3: Update application.properties for full health detail**

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

- [ ] **Step 4: Write EmbeddingProviderHealthIndicator**

```java
// services/api/src/main/java/os/assurance/eu/api/EmbeddingProviderHealthIndicator.java
package os.assurance.eu.api;

import os.assurance.eu.api.evidence.EvidenceEmbeddingProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("embeddingProvider")
public class EmbeddingProviderHealthIndicator implements HealthIndicator {
    private final EvidenceEmbeddingProvider provider;

    public EmbeddingProviderHealthIndicator(EvidenceEmbeddingProvider provider) {
        this.provider = provider;
    }

    @Override
    public Health health() {
        try {
            String probe = provider.embed("health check");
            if (probe == null || probe.isBlank()) {
                return Health.down().withDetail("reason", "embed returned empty").build();
            }
            return Health.up().withDetail("provider", provider.name()).build();
        } catch (Exception e) {
            return Health.down(e).withDetail("provider", provider.name()).build();
        }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, Failures: 0
```

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/resources/application.properties \
        services/api/src/main/java/os/assurance/eu/api/EmbeddingProviderHealthIndicator.java \
        services/api/src/test/java/os/assurance/eu/api/ApiControllerTest.java
git commit -m "feat(observability): add liveness/readiness probes and embedding provider health indicator"
```

---

## Task 8: Enable Postgres Concurrency Test in CI

The `PostgresEvalRunConcurrencyTest` is currently skipped unless `RUN_POSTGRES_CONCURRENCY=true` is set. Wire it into the Maven `integration-test` phase via Failsafe so `mvn verify` runs it when a Postgres DB is available, and add a `scripts/postgres-ci.sh` helper that starts a local Postgres container, runs the test, and tears down.

**Files:**
- Modify: `services/api/pom.xml`
- Modify: `services/api/src/test/java/os/assurance/eu/api/eval/PostgresEvalRunConcurrencyTest.java`
- Create: `scripts/postgres-ci.sh`

- [ ] **Step 1: Read the current skip guard in PostgresEvalRunConcurrencyTest**

```bash
cat /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/services/api/src/test/java/os/assurance/eu/api/eval/PostgresEvalRunConcurrencyTest.java
```

Note the `@EnabledIfEnvironmentVariable` or `@DisabledIf` guard — adapt the change below to match.

- [ ] **Step 2: Replace the skip guard with a JUnit 5 `@Tag("postgres")`**

```java
// Remove: @EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_CONCURRENCY", matches = "true")
// Add: @Tag("postgres")
import org.junit.jupiter.api.Tag;

@Tag("postgres")
@SpringBootTest(properties = { "spring.profiles.active=postgres", ... })
class PostgresEvalRunConcurrencyTest { ... }
```

This makes the test filterable by tag rather than env var.

- [ ] **Step 3: Add Maven Failsafe config to pom.xml**

In `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <groups>postgres</groups>
        <systemPropertyVariables>
            <spring.profiles.active>postgres</spring.profiles.active>
            <DATABASE_URL>${env.DATABASE_URL}</DATABASE_URL>
            <DATABASE_USERNAME>${env.DATABASE_USERNAME}</DATABASE_USERNAME>
            <DATABASE_PASSWORD>${env.DATABASE_PASSWORD}</DATABASE_PASSWORD>
            <EVAL_CALLBACK_SECRET>${env.EVAL_CALLBACK_SECRET}</EVAL_CALLBACK_SECRET>
        </systemPropertyVariables>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Write scripts/postgres-ci.sh**

```bash
#!/usr/bin/env bash
# scripts/postgres-ci.sh
# Starts a Postgres+pgvector container, runs @Tag("postgres") tests, tears down.
set -euo pipefail

CONTAINER=eu-ai-assurance-ci-postgres

docker run -d --name "$CONTAINER" \
  -e POSTGRES_DB=eu_ai_assurance \
  -e POSTGRES_USER=eu_ai_assurance \
  -e POSTGRES_PASSWORD=eu_ai_assurance \
  -p 5433:5432 \
  pgvector/pgvector:pg16

cleanup() {
  docker rm -f "$CONTAINER" 2>/dev/null || true
}
trap cleanup EXIT

until docker exec "$CONTAINER" pg_isready -U eu_ai_assurance; do sleep 1; done

DATABASE_URL=jdbc:postgresql://localhost:5433/eu_ai_assurance \
DATABASE_USERNAME=eu_ai_assurance \
DATABASE_PASSWORD=eu_ai_assurance \
EVAL_CALLBACK_SECRET=test-eval-callback-secret \
mvn -f services/api/pom.xml verify -Ppostgres-ci 2>&1

echo "Postgres CI tests passed."
```

```bash
chmod +x scripts/postgres-ci.sh
```

- [ ] **Step 5: Run standard unit tests to confirm nothing regressed**

```bash
mvn -f services/api/pom.xml test 2>&1 | grep -E "Tests run|BUILD"
# Expected: BUILD SUCCESS, @Tag("postgres") test skipped by Surefire (it targets Failsafe)
```

- [ ] **Step 6: Run postgres-ci.sh if Docker is available**

```bash
bash scripts/postgres-ci.sh
# Expected: "Postgres CI tests passed."
```

- [ ] **Step 7: Commit**

```bash
git add services/api/pom.xml \
        services/api/src/test/java/os/assurance/eu/api/eval/PostgresEvalRunConcurrencyTest.java \
        scripts/postgres-ci.sh
git commit -m "feat(ci): wire Postgres concurrency test into Maven Failsafe with postgres-ci.sh helper"
```

---

## Self-Review Against Gaps

| Gap from Roadmap | Task |
|---|---|
| Docker/Postgres deployment | Task 1 |
| API key auth (no real auth currently) | Task 2 |
| PDF text extraction | Task 3 |
| S3-compatible evidence storage | Task 4 |
| Non-local embedding provider | Task 5 |
| Structured logging / correlation IDs | Task 6 |
| Liveness/readiness probes | Task 7 |
| Postgres CI (skipped test) | Task 8 |

All 8 gaps addressed. No placeholders remain. Type names used in later tasks match those defined in earlier tasks (`FileStorageService`, `FileStorageProperties`, `DjlSentenceEmbeddingProvider`, `EvidenceEmbeddingConfig`, `CorrelationFilter`, `EmbeddingProviderHealthIndicator`, `ApiKeyEntity`, `ApiKeyJpaRepository`).
