# ARCHITECTURE

## High-Level System Design

Devernance is architected as a **multi-tenant modular monolith**, deliberately avoiding microservice complexity. The system consists of two primary applications:
1. **`services/api`**: A Java 17 / Spring Boot 3.3.7 backend owning all domain logic, persistence, security, eval queue execution, and audit verification.
2. **`apps/dashboard`**: A Next.js 16 (16.2.9) / React 19 / TypeScript / Tailwind CSS v4 frontend serving as both the UI and a Backend-for-Frontend (BFF) security proxy.

The system relies on a single database (PostgreSQL in production/Docker, or embedded H2 for local development and fast CI). There is no external message broker (such as Kafka or RabbitMQ) in the critical path — background evaluation processing runs via a database-backed worker queue.

```
+-------------------------------------------------------------------------+
|                               Browser Client                            |
+-------------------------------------------------------------------------+
                                     |
                                     | HTTPS (httpOnly Session Cookies)
                                     v
+-------------------------------------------------------------------------+
| apps/dashboard (Next.js 16 BFF Proxy on Vercel / Node)                   |
| - Marketing & Legal Pages                                               |
| - Authenticated Product Shell (Middleware Guard)                        |
| - /api/proxy (Server-side Bearer token injection, session refresh)      |
+-------------------------------------------------------------------------+
                                     |
                                     | HTTP/REST (Bearer JWT / API Key)
                                     v
+-------------------------------------------------------------------------+
| services/api (Spring Boot 3.3.7 / Java 17 Monolith)                     |
|                                                                         |
|  +-------------------+  +-------------------+  +---------------------+  |
|  | TenantContext     |  | SystemRegistry    |  | ReleaseGateEngine   |  |
|  | Filter (RBAC)     |  | & Risk Classifier |  | (PASS/REVIEW/BLOCK) |  |
|  +-------------------+  +-------------------+  +---------------------+  |
|  | ControlsCatalog   |  | Evidence RAG      |  | EvalQueueWorker     |  |
|  | & Status Tracker  |  | (Ingest/Vector)   |  | (SKIP LOCKED poll)  |  |
|  +-------------------+  +-------------------+  +---------------------+  |
|  | DataContracts     |  | Staged Approvals  |  | Hash-Chained Audit  |  |
|  | & Drift Detector  |  | & Oversight Logs  |  | (HMAC-SHA-256)      |  |
|  +-------------------+  +-------------------+  +---------------------+  |
|  | Determination     |  | Certification     |  | Regulatory Change   |  |
|  | Questionnaire     |  | Readiness Engine  |  | Monitor Feed        |  |
|  +-------------------+  +-------------------+  +---------------------+  |
+-------------------------------------------------------------------------+
                                     |
                                     | JDBC / JPA (Flyway Migrations V1-V16)
                                     v
+-------------------------------------------------------------------------+
| PostgreSQL 16 (or H2 in-memory)                                         |
| - Tenant-isolated tables with tenant_id discriminator                   |
| - pgvector HNSW cosine index (V4, optional)                              |
| - Append-only audit_events with prev_event_hash                         |
+-------------------------------------------------------------------------+
```

---

## Component Architecture & Responsibilities

### 1. Backend Service (`services/api`)

Organized under `os.assurance.eu.api` into clean domain packages rather than distributed microservices:
- **`system`**: Manages AI system registrations, vendor/model metadata, sector tagging, decision impact levels, and legal risk classifications (`MINIMAL`, `LIMITED`, `HIGH`, `PROHIBITED`).
- **`control`**: Defines the EU AI Act control catalog and manages per-system control statuses (`PASS`, `REVIEW`, `BLOCKED`).
- **`evidence`**: Ingests compliance documents, validates URIs/checksums, strips prompt injection attempts, chunks text, generates embeddings, and handles tenant/system-isolated vector similarity search with mandatory source citations.
- **`eval`**: Manages test datasets, thresholds, and scored runs (faithfulness, relevance, refusal, bias, latency, cost). Runs an asynchronous queue worker polling every 5000ms (`assurance.eval.worker.poll-interval-ms`) using `SELECT ... FOR UPDATE SKIP LOCKED` for safe multi-instance concurrency.
- **`contract`**: Tracks upstream data schemas, data contracts, and drift events. Open drift events (`BREACH`) automatically update system contract status.
- **`workflow`**: Manages multi-stage sequential human approvals (`ENG_LEAD_REVIEW` → `COMPLIANCE_REVIEW` → `LEGAL_SIGNOFF`). Requires recorded oversight evidence for high-risk legal sign-off.
- **`audit`**: Implements an append-only audit trail with `prev_event_hash` and `event_hash` calculated via HMAC-SHA-256 over canonical JSON payloads. Exposes `/audit/verify` and `/audit-events/verify-chain`.
- **`auth` / `tenant`**: Handles password authentication, JWT issuing/refresh, JWKS endpoints, Google & Microsoft OAuth 2.0 flows, API key validation (`ak_live_*`), and enforces strict tenant isolation via `TenantContextFilter`.
- **`determination`**, **`readiness`**, **`regmonitor`**, **`sector`**: Assistive layers for obligation mapping, 0–100 readiness scoring, regulatory update tracking, and sector pack SPIs (Insurance, HR, Finance).

### 2. Frontend & BFF Layer (`apps/dashboard`)

Built with Next.js 16, React 19, TypeScript, Tailwind CSS v4, and TanStack Query v5:
- **BFF Architecture**: The browser never directly stores or transmits raw JWT secret tokens or client-supplied tenant/actor headers. All session authentication uses `httpOnly` secure cookies.
- **BFF Proxy (`/api/proxy`)**: Intercepts browser requests, retrieves the session access token, injects the `Authorization: Bearer <token>` header, and forwards the request to the Spring Boot API. Handles transparent token refresh when access tokens expire.
- **OAuth BFF Endpoints**: Features `/api/auth/oauth/google/start` and `/api/auth/oauth/google/callback` to mediate OAuth flow redirects securely between browser, Google IdP, and API.
- **Product Shell**: Contains 9 authenticated product views (`/command`, `/systems`, `/approvals`, `/evidence`, `/evals`, `/contracts`, `/audit`, `/readiness`, `/reg-monitor`) protected by `middleware.ts`.
- **Visualizations**: Interactive data-contract lineage DAG powered by `@xyflow/react`.

---

## Evidence RAG & Dual-Embedding Engine

The evidence retrieval pipeline consists of:
1. **`EvidenceIngestionGuard`**: Validates document metadata, source URI schemes, content length, and SHA-256 checksums. Strips lines containing prompt-injection patterns before embedding.
2. **`TextExtractionService`**: Fetches external HTTPS documents using Apache HttpClient5 with a single-lookup `DnsResolver` to prevent SSRF and DNS-rebinding attacks.
3. **`EvidenceChunker`**: Segments document text into configurable overlap chunks.
4. **`EvidenceEmbeddingService`**: Pluggable provider seam implementing `EvidenceEmbeddingProvider`:
   - **`local-hash`** (Default / Dev / H2): Deterministic string-hashing vector generation requiring no external model weights. Ensures 100% offline, reproducible CI/dev test runs.
   - **`djl-sentence`** (Production / Postgres): Uses Deep Java Library (DJL) + ONNX Runtime running `sentence-transformers/all-MiniLM-L6-v2`. Pairs with a pgvector HNSW cosine index (`V4` Flyway migration).

---

## Data Flow & Event Processing

### 1. System Release Decision Flow
```text
CI / Deploy Pipeline or Dashboard
       |
       | GET /ci/release-gate?systemId={id}
       v
ReleaseGateService.calculate()
       |
       +---> Check Risk Classification (PROHIBITED -> BLOCKED)
       +---> Check Control Statuses (Any BLOCKED control -> BLOCKED)
       +---> Check Data Contract Status (BREACH -> BLOCKED)
       +---> Check Eval Scores (Score < 78 -> BLOCKED, Score < 85 -> REVIEW)
       +---> Check Evidence Coverage (Coverage < 82% -> REVIEW)
       +---> Check Oversight Evidence (HIGH risk missing signoff evidence -> BLOCKED)
       v
Returns ReleaseGateResponse { decision: PASS|REVIEW|BLOCKED, exitCode: 0|2|1 }
```

### 2. Audit Event Hash-Chaining Flow
```text
Event Trigger (e.g. Risk Class Change, Approval Stage Signed)
       |
       v
AuditService.logEvent(tenantId, actorId, eventType, resourceId, payload)
       |
       v
AuditChainHasher.computeHash(tenantId, id, prevEventHash, actorId, eventType, resourceType, resourceId, payloadJson, createdAt)
       |
       v
Insert into audit_events (prev_event_hash, event_hash, retain_until=7_YEARS)
```

---

## Technology Choices & Architectural Trade-offs

| Component / Choice | Architectural Rationale | Trade-off / Limitation |
|---|---|---|
| **Spring Boot Monolith** | Tight coupling of compliance rules (eval + contract + controls + audit needed simultaneously) makes monolith optimal; eliminates network latency & distributed transaction failure modes. | Requires vertical scaling rather than independent microservice scaling. |
| **DB-Backed Worker Queue** | Avoids introducing Kafka/RabbitMQ stateful infrastructure. `SELECT ... FOR UPDATE SKIP LOCKED` guarantees safe concurrent execution across replicas. | Polling interval floor (5000ms) limits worker dispatch throughput; not suited for ultra-high-frequency real-time execution. |
| **BFF Proxy + httpOnly Cookies** | Prevents browser-side JavaScript from accessing JWT tokens; eliminates client-side header spoofing of `X-Tenant-Id`. | Dashboard must run as an active Server/Node container (e.g. Vercel) rather than static CDN export. |
| **Dual Embedding Seam (`local-hash` vs `djl-sentence`)** | Enables fast, deterministic, offline CI unit/integration tests without downloading 90MB ONNX models or requiring GPUs. | `local-hash` lacks semantic awareness; production must run `djl-sentence` with matching vector dimension. |
| **pgvector HNSW over Vector DB** | Keeps all relational and vector state inside Postgres; simplifies backup/restore, ACID transactions, and tenant isolation. | Higher memory usage on Postgres host during large-scale vector indexing compared to dedicated specialized vector DBs. |

---

## Deployment Topology & Infrastructure

- **Local Native Development**: Run `./scripts/run-local-dev.sh` to start Spring Boot API (port 8080, H2 database) and Next.js Dashboard (port 3000) simultaneously with Google OAuth support.
- **Local Containerized (Docker Compose)**: `infra/docker-compose.yml` provisions Postgres 16 and Spring Boot API. Optional profiles (`--profile dashboard`, `--profile minio`) add the self-hosted dashboard container and MinIO object store.
- **Production Topology**:
  - **Dashboard**: Hosted on Vercel (`apps/dashboard` root), live at `https://euassuranceai.souravamseekar.com`.
  - **API**: Deployed as a containerized Java service on Cloud Run / ECS / K8s behind TLS.
  - **Database**: Managed PostgreSQL 16 instance with pgvector extension enabled.
- **Infrastructure as Code**: `infra/terraform/` contains modular HCL skeletons (validating via `terraform-validate.sh` in CI).
