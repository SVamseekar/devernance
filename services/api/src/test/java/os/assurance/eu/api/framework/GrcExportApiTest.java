package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import os.assurance.eu.api.email.EmailSender;
import os.assurance.eu.api.system.EvidencePackSealer;
import os.assurance.eu.api.tenant.UserJpaRepository;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "assurance.app.base-url=https://app.test",
    "assurance.security.auth-rate.per-ip-per-15m=1000",
    "assurance.auth.email.cooldown-seconds=0",
    "spring.jackson.mapper.accept-case-insensitive-enums=true"
})
@AutoConfigureMockMvc
class GrcExportApiTest {
  static final String HEADER =
      "document_id,document_title,type,created_date,sha256,pack_content_sha256,pack_signature_key_id";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserJpaRepository users;
  @MockitoSpyBean EmailSender emailSender;
  TestWorkspaces workspaces;

  @BeforeEach
  void setUp() {
    workspaces = new TestWorkspaces(mockMvc, json, users, emailSender, jdbc);
  }

  private MvcResult download(TestWorkspaces.Workspace w, String systemId) throws Exception {
    return mockMvc.perform(get("/api/v1/systems/{id}/grc-export", systemId).header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn();
  }

  private static Map<String, String> unzip(byte[] zip) throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }

  @Test
  void theZipHoldsTheIndexTheSignedPackAndTheVerificationGuide() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    MvcResult result = download(w, id);
    assertThat(result.getResponse().getContentType()).isEqualTo("application/zip");
    assertThat(result.getResponse().getHeader("Content-Disposition"))
        .startsWith("attachment; filename=\"grc-export-" + id + "-").endsWith(".zip\"");
    Map<String, String> files = unzip(result.getResponse().getContentAsByteArray());
    assertThat(files.keySet())
        .containsExactly("evidence-index.csv", "evidence-pack.json", "VERIFY_EVIDENCE_PACK.md");
    assertThat(result.getResponse().getHeader("X-Content-Sha256"))
        .isEqualTo(json.readTree(files.get("evidence-pack.json")).get("contentSha256").asText());
  }

  @Test
  void theIndexHasOneRowPerIndexedDocumentTiedToThePack() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    String policy = workspaces.indexDocument(w, id, "POLICY", "Oversight policy, \"v3\"");
    String card = workspaces.indexDocument(w, id, "MODEL_CARD", "=Model card");
    Map<String, String> files = unzip(download(w, id).getResponse().getContentAsByteArray());
    JsonNode pack = json.readTree(files.get("evidence-pack.json"));
    String kid = JWSObject.parse(pack.get("signature").asText()).getHeader().getKeyID();

    String csv = files.get("evidence-index.csv");
    assertThat(csv).startsWith("﻿" + HEADER + "\r\n");
    String[] lines = csv.split("\r\n");
    assertThat(lines).hasSize(3);
    String tail = "," + pack.get("contentSha256").asText() + "," + kid;
    assertThat(lines[1]).startsWith(policy + ",\"Oversight policy, \"\"v3\"\"\",POLICY,").endsWith(tail);
    // A title that a spreadsheet would run as a formula is neutralised.
    assertThat(lines[2]).startsWith(card + ",'=Model card,MODEL_CARD,").endsWith(tail);
    assertThat(lines[1].split(",")[lines[1].split(",").length - 3]).hasSize(64);
  }

  @Test
  void aSystemWithNoDocumentsStillExportsWithAnEmptyIndex() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    Map<String, String> files = unzip(download(w, id).getResponse().getContentAsByteArray());
    assertThat(files.get("evidence-index.csv")).isEqualTo("﻿" + HEADER + "\r\n");
  }

  @Test
  void thePackInTheZipVerifiesAgainstThePublishedKeysAndItsOwnHash() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    workspaces.indexDocument(w, id, "POLICY", "Oversight policy");
    String packJson = unzip(download(w, id).getResponse().getContentAsByteArray()).get("evidence-pack.json");
    JsonNode pack = json.readTree(packJson);
    String jwks = mockMvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();

    JWSObject jws = JWSObject.parse(pack.get("signature").asText());
    RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeyByKeyId(jws.getHeader().getKeyID());
    assertThat(jws.verify(new RSASSAVerifier(key))).isTrue();
    assertThat(jws.getPayload().toJSONObject().get("contentSha256")).isEqualTo(pack.get("contentSha256").asText());
    assertThat(jws.getPayload().toJSONObject().get("systemId")).isEqualTo(id);

    // What VERIFY_EVIDENCE_PACK.md tells the recipient to do with the file from the zip.
    Map<String, Object> body = json.readValue(packJson, new TypeReference<>() {});
    body.remove("contentSha256");
    body.remove("signature");
    body.put("conformity", Map.of());
    assertThat(EvidencePackSealer.contentSha256(body)).isEqualTo(pack.get("contentSha256").asText());
  }

  @Test
  void theExportIsRecordedInTheAuditLedger() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    download(w, id);
    String pack = mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", id).header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    boolean recorded = false;
    for (JsonNode event : json.readTree(pack).get("auditEvents")) {
      if ("evidence_pack.exported".equals(event.get("eventType").asText())
          && "grc-zip".equals(event.get("payload").path("format").asText())) {
        recorded = true;
      }
    }
    assertThat(recorded).isTrue();
  }

  @Test
  void theBundledVerificationGuideMatchesThePublishedOne() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    String bundled = unzip(download(w, id).getResponse().getContentAsByteArray()).get("VERIFY_EVIDENCE_PACK.md");
    assertThat(bundled).isEqualTo(Files.readString(Path.of("../../docs/VERIFY_EVIDENCE_PACK.md")));
  }

  @Test
  void teamAndFreePlansGetPaymentRequired() throws Exception {
    var team = workspaces.signUp();
    String teamSystem = workspaces.createSystem(team, "Claims triage", "high", "");
    workspaces.setPlan(team, "team");
    mockMvc.perform(get("/api/v1/systems/{id}/grc-export", teamSystem).header("Authorization", team.bearer()))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.code").value("feature_integrations"));

    var free = workspaces.signUp();
    String freeSystem = workspaces.createSystem(free, "Claims triage", "high", "");
    workspaces.endTrial(free);
    mockMvc.perform(get("/api/v1/systems/{id}/grc-export", freeSystem).header("Authorization", free.bearer()))
        .andExpect(status().isPaymentRequired());
  }

  @Test
  void anotherWorkspacesSystemIsNotFound() throws Exception {
    var owner = workspaces.signUp();
    String id = workspaces.createSystem(owner, "Claims triage", "high", "");
    var other = workspaces.signUp();
    mockMvc.perform(get("/api/v1/systems/{id}/grc-export", id).header("Authorization", other.bearer()))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/systems/{id}/grc-export", UUID.randomUUID())
            .header("Authorization", owner.bearer()))
        .andExpect(status().isNotFound());
  }
}
