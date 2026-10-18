package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import os.assurance.eu.api.email.EmailMessage;
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
class FrameworkApiTest {
  private static final String PASSWORD = "correct-horse-battery";
  static final String DISCLAIMER =
      "Mapping shows which evidence is relevant to each framework item. It is not an assessment of whether the system meets the framework.";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserJpaRepository users;
  @MockitoSpyBean EmailSender emailSender;

  record Workspace(String bearer, UUID tenantId, String systemId) {}

  Workspace newWorkspace(String riskClass) throws Exception {
    String email = "fw-" + UUID.randomUUID() + "@acme.example";
    mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"organisationName\":\"Fw " + UUID.randomUUID() + "\"}"))
        .andExpect(status().isAccepted());
    ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
    String body = captor.getAllValues().stream().filter(m -> email.equals(m.to()))
        .reduce((a, b) -> b).orElseThrow().textBody();
    String token = body.substring(body.indexOf("token=") + 6).split("\\s")[0];
    String verified = mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String bearer = "Bearer " + json.readTree(verified).get("accessToken").asText();
    UUID tenantId = users.findByEmailIgnoreCase(email).orElseThrow().tenantId();
    String created = mockMvc.perform(post("/api/v1/systems").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Claims triage","owner":"Ops","purpose":"framework test","riskClass":"%s",
                 "riskBasis":"fixture","deploymentRegion":"EU","evidenceCoverage":0,"evalScore":0,
                 "dataContractStatus":"warning","openGaps":[]}""".formatted(riskClass)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return new Workspace(bearer, tenantId, json.readTree(created).get("id").asText());
  }

  void endTrial(Workspace w) {
    jdbc.update("update tenants set trial_ends_at = ? where id = ?",
        Timestamp.from(Instant.now().minusSeconds(60)), w.tenantId());
  }

  @Test
  void aTrialWorkspaceSeesEveryNistCategoryWithTheDisclaimer() throws Exception {
    Workspace w = newWorkspace("limited");
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/nist-ai-rmf", w.systemId())
            .header("Authorization", w.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.framework").value("nist-ai-rmf"))
        .andExpect(jsonPath("$.version").value("1.0"))
        .andExpect(jsonPath("$.reviewed").value(false))
        .andExpect(jsonPath("$.disclaimer").value(DISCLAIMER))
        .andExpect(jsonPath("$.items.length()").value(19))
        .andExpect(jsonPath("$.items[0].status").value("no_evidence"));
  }

  @Test
  void theFreePlanGetsPaymentRequiredWithTheUpgradeCode() throws Exception {
    Workspace w = newWorkspace("limited");
    endTrial(w);
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/nist-ai-rmf", w.systemId())
            .header("Authorization", w.bearer()))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.code").value("feature_nist_crosswalk"));
  }

  @Test
  void anUnknownFrameworkOrSystemIsNotFound() throws Exception {
    Workspace w = newWorkspace("limited");
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/made-up", w.systemId())
            .header("Authorization", w.bearer()))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/nist-ai-rmf", UUID.randomUUID())
            .header("Authorization", w.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void theFreePlanPackOmitsFrameworksAndStillExports() throws Exception {
    Workspace w = newWorkspace("limited");
    endTrial(w);
    String pack = mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", w.systemId())
            .header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(pack).has("frameworks")).isFalse();
    assertThat(recomputedSha(pack)).isEqualTo(json.readTree(pack).get("contentSha256").asText());
  }

  @Test
  void aTrialPackCarriesTheCrosswalkInsideTheSeal() throws Exception {
    Workspace w = newWorkspace("limited");
    String pack = mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", w.systemId())
            .header("Authorization", w.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.frameworks['nist-ai-rmf'].items.length()").value(19))
        .andExpect(jsonPath("$.frameworks['nist-ai-rmf'].disclaimer").value(DISCLAIMER))
        .andExpect(jsonPath("$.frameworks['colorado-sb26-189'].type").value("checklist"))
        .andReturn().getResponse().getContentAsString();
    // The hash a customer recomputes from the exported JSON must cover the frameworks section.
    assertThat(recomputedSha(pack)).isEqualTo(json.readTree(pack).get("contentSha256").asText());
  }

  /** What a verifier does: drop the seal fields, restore the hidden conformity key, hash canonically. */
  private String recomputedSha(String packJson) throws Exception {
    Map<String, Object> payload = json.readValue(packJson, new TypeReference<>() {});
    payload.remove("contentSha256");
    payload.remove("signature");
    payload.put("conformity", Map.of());
    return EvidencePackSealer.contentSha256(payload);
  }
}
