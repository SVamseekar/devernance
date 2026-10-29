package os.assurance.eu.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import os.assurance.eu.api.auth.JwtService;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "spring.jackson.mapper.accept-case-insensitive-enums=true"
})
@AutoConfigureMockMvc
class ApiKeyApiTest {
  private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000105");
  private static final UUID AUDITOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper json;
  @Autowired JwtService jwt;
  @Autowired UserJpaRepository users;
  @Autowired TenantJpaRepository tenants;
  @Autowired ApiKeyJpaRepository apiKeys;

  @BeforeEach
  void seed() {
    Instant now = Instant.now();
    if (users.findById(ADMIN_ID).isEmpty()) {
      users.save(new UserEntity(ADMIN_ID, TenantContext.DEFAULT_TENANT_ID, "admin@example.com", UserRole.ADMIN, now));
    }
    if (users.findById(AUDITOR_ID).isEmpty()) {
      users.save(new UserEntity(AUDITOR_ID, TenantContext.DEFAULT_TENANT_ID, "auditor@example.com", UserRole.AUDITOR, now));
    }
  }

  private String bearer(UUID user, UUID tenant, UserRole role) {
    return "Bearer " + jwt.issueAccessToken(user, tenant, role);
  }

  private String adminBearer() {
    return bearer(ADMIN_ID, TenantContext.DEFAULT_TENANT_ID, UserRole.ADMIN);
  }

  private JsonNode createKey(String name) throws Exception {
    String body = mockMvc.perform(post("/api/v1/api-keys").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(body);
  }

  private String createSystem() throws Exception {
    String body = mockMvc.perform(post("/api/v1/systems").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Key Fixture","owner":"Ops","purpose":"api key test","riskClass":"limited",
                 "riskBasis":"fixture","deploymentRegion":"EU","evidenceCoverage":80,"evalScore":80,
                 "dataContractStatus":"warning","openGaps":[]}"""))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("id").asText();
  }

  @Test
  void createdKeyHasTheDocumentedShapeAndAuthenticatesTheCiGate() throws Exception {
    JsonNode created = createKey("GitHub Actions");
    String key = created.get("key").asText();
    assertThat(key).startsWith("dvn_").hasSize(47);
    assertThat(created.get("prefix").asText()).isEqualTo(key.substring(0, 12));

    String systemId = createSystem();
    mockMvc.perform(get("/api/v1/ci/release-gate").param("systemId", systemId).header("X-Api-Key", key))
        .andExpect(status().isOk());
  }

  @Test
  void keysIssuedBeforeTheRenameStillAuthenticate() throws Exception {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String legacy = "aos_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    apiKeys.save(new ApiKeyEntity(UUID.randomUUID(), ApiKeyHasher.sha256Hex(legacy), TenantContext.DEFAULT_TENANT_ID,
        ADMIN_ID, Instant.now(), "Pre-rename key", legacy.substring(0, 12)));

    String systemId = createSystem();
    mockMvc.perform(get("/api/v1/ci/release-gate").param("systemId", systemId).header("X-Api-Key", legacy))
        .andExpect(status().isOk());
  }

  @Test
  void listNeverContainsTheRawKeyAndShowsLastUse() throws Exception {
    JsonNode created = createKey("Listing");
    String key = created.get("key").asText();
    String systemId = createSystem();
    mockMvc.perform(get("/api/v1/ci/release-gate").param("systemId", systemId).header("X-Api-Key", key))
        .andExpect(status().isOk());

    String listed = mockMvc.perform(get("/api/v1/api-keys").header("Authorization", adminBearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(listed).doesNotContain(key).doesNotContain("keyHash").contains(created.get("prefix").asText());
    JsonNode row = null;
    for (JsonNode n : json.readTree(listed)) {
      if (n.get("id").asText().equals(created.get("id").asText())) row = n;
    }
    assertThat(row).isNotNull();
    assertThat(row.get("name").asText()).isEqualTo("Listing");
    assertThat(row.get("lastUsedAt").isNull()).isFalse();
  }

  @Test
  void revokedKeyIsRejectedAndDisappearsFromTheList() throws Exception {
    JsonNode created = createKey("To revoke");
    String key = created.get("key").asText();
    String systemId = createSystem();

    mockMvc.perform(delete("/api/v1/api-keys/{id}", created.get("id").asText()).header("Authorization", adminBearer()))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/v1/ci/release-gate").param("systemId", systemId).header("X-Api-Key", key))
        .andExpect(status().isUnauthorized());
    String listed = mockMvc.perform(get("/api/v1/api-keys").header("Authorization", adminBearer()))
        .andReturn().getResponse().getContentAsString();
    assertThat(listed).doesNotContain(created.get("id").asText());
  }

  @Test
  void auditorCannotCreateKeys() throws Exception {
    mockMvc.perform(post("/api/v1/api-keys")
            .header("Authorization", bearer(AUDITOR_ID, TenantContext.DEFAULT_TENANT_ID, UserRole.AUDITOR))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"nope\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anotherTenantsAdminCannotRevokeTheKey() throws Exception {
    JsonNode created = createKey("Tenant A key");
    UUID tenantB = UUID.randomUUID();
    UUID adminB = UUID.randomUUID();
    tenants.save(new TenantEntity(tenantB, "Tenant B", "trial", "EU", Instant.now()));
    users.save(new UserEntity(adminB, tenantB, "admin-" + adminB + "@b.example", UserRole.ADMIN, Instant.now()));
    mockMvc.perform(delete("/api/v1/api-keys/{id}", created.get("id").asText())
            .header("Authorization", bearer(adminB, tenantB, UserRole.ADMIN)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aKeyCannotMintOrRevokeKeys() throws Exception {
    JsonNode created = createKey("Bootstrap");
    String key = created.get("key").asText();
    mockMvc.perform(post("/api/v1/api-keys").header("X-Api-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"persistence\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/v1/api-keys/{id}", created.get("id").asText()).header("X-Api-Key", key))
        .andExpect(status().isForbidden());
  }

  @Test
  void aKeyCannotInviteUsersOrProvisionTenants() throws Exception {
    String key = createKey("Invite attempt").get("key").asText();
    mockMvc.perform(post("/api/v1/admin/users/invites").header("X-Api-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"mallory@evil.example\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/admin/tenants").header("X-Api-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Evil\",\"adminEmail\":\"a@evil.example\",\"adminPassword\":\"long-enough-password\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void aNewKeyCanOnlyReadTheReleaseGateUnlessFullAccessIsAskedFor() throws Exception {
    JsonNode created = createKey("CI gate");
    assertThat(created.get("scope").asText()).isEqualTo("RELEASE_GATE");
    String key = created.get("key").asText();
    String systemId = createSystem();

    mockMvc.perform(get("/api/v1/ci/release-gate").param("systemId", systemId).header("X-Api-Key", key))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/systems").header("X-Api-Key", key))
        .andExpect(status().isForbidden());
    mockMvc.perform(patch("/api/v1/systems/{id}", systemId).header("X-Api-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content("{\"riskClass\":\"MINIMAL\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/ci/release-gate").param("systemId", systemId).header("X-Api-Key", key))
        .andExpect(status().isForbidden());
  }

  @Test
  void aFullAccessKeyIsExplicitAndListedAsSuch() throws Exception {
    String body = mockMvc.perform(post("/api/v1/api-keys").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Evidence upload bot\",\"scope\":\"FULL\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    JsonNode created = json.readTree(body);
    assertThat(created.get("scope").asText()).isEqualTo("FULL");
    mockMvc.perform(get("/api/v1/systems").header("X-Api-Key", created.get("key").asText()))
        .andExpect(status().isOk());

    String listed = mockMvc.perform(get("/api/v1/api-keys").header("Authorization", adminBearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    for (JsonNode row : json.readTree(listed)) {
      if (row.get("id").asText().equals(created.get("id").asText())) {
        assertThat(row.get("scope").asText()).isEqualTo("FULL");
      }
    }
  }

  @Test
  void anUnknownScopeIsRejected() throws Exception {
    mockMvc.perform(post("/api/v1/api-keys").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"scope\":\"ROOT\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void keysCreatedBeforeScopesKeepFullAccess() throws Exception {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String older = "dvn_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    apiKeys.save(new ApiKeyEntity(UUID.randomUUID(), ApiKeyHasher.sha256Hex(older), TenantContext.DEFAULT_TENANT_ID,
        ADMIN_ID, Instant.now(), "Older key", older.substring(0, 12)));
    mockMvc.perform(get("/api/v1/systems").header("X-Api-Key", older))
        .andExpect(status().isOk());
  }

  @Test
  void malformedKeysAreRejectedAsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/systems").header("X-Api-Key", "dvn_too-short"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void overlongNameIsRejected() throws Exception {
    mockMvc.perform(post("/api/v1/api-keys").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "x".repeat(81) + "\"}"))
        .andExpect(status().isBadRequest());
  }
}
