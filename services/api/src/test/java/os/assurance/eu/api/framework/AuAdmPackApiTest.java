package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import os.assurance.eu.api.email.EmailSender;
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
class AuAdmPackApiTest {
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

  private JsonNode system(JsonNode pack, String id) {
    for (JsonNode s : pack.get("systems")) {
      if (id.equals(s.get("systemId").asText())) {
        return s;
      }
    }
    throw new AssertionError("system " + id + " not in pack");
  }

  @Test
  void classifiesEachSystemAndDraftsWordingOnlyFromRecordedFacts() throws Exception {
    var w = workspaces.signUp();
    String claims = workspaces.createSystem(w, "Claims triage", "high", """
        "decisionImpact":"claim prioritisation affecting payout timing","affectedUsers":["claimants"],
        "dataSources":["claims history"]""");
    String blank = workspaces.createSystem(w, "Fraud scorer", "high", """
        "decisionImpact":"","affectedUsers":["claimants"],"dataSources":["payment records"]""");
    String internal = workspaces.createSystem(w, "Doc search", "minimal", "\"affectedUsers\":[]");

    String body = mockMvc.perform(get("/api/v1/packs/au-adm").header("Authorization", w.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.obligationStart").value("2026-12-10"))
        .andExpect(jsonPath("$.elements.length()").value(3))
        .andReturn().getResponse().getContentAsString();
    JsonNode pack = json.readTree(body);
    assertThat(pack.get("source").asText()).contains("oaic.gov.au");
    assertThat(pack.get("disclaimer").asText()).contains("not legal advice");

    JsonNode included = system(pack, claims);
    assertThat(included.get("status").asText()).isEqualTo("include");
    assertThat(included.get("missing")).isEmpty();
    String draft = included.get("draftStatement").asText();
    assertThat(draft).startsWith("Draft for review by your privacy counsel:");
    assertThat(draft).contains("Claims triage", "claim prioritisation affecting payout timing", "claimants",
        "claims history", "prioritise incoming insurance claims");
    assertThat(draft).contains("A person reviews these decisions: to be confirmed.");

    JsonNode needsInput = system(pack, blank);
    assertThat(needsInput.get("status").asText()).isEqualTo("needs_input");
    assertThat(needsInput.get("missing")).extracting(JsonNode::asText).containsExactly("decisionImpact");
    assertThat(needsInput.get("draftStatement").isNull()).isTrue();

    JsonNode notApplicable = system(pack, internal);
    assertThat(notApplicable.get("status").asText()).isEqualTo("not_applicable");
    assertThat(notApplicable.get("draftStatement").isNull()).isTrue();
  }

  @Test
  void theFreePlanGetsPaymentRequired() throws Exception {
    var w = workspaces.signUp();
    workspaces.endTrial(w);
    mockMvc.perform(get("/api/v1/packs/au-adm").header("Authorization", w.bearer()))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.code").value("feature_au_adm_pack"));
  }

  @Test
  void theTeamPlanGetsPaymentRequired() throws Exception {
    var w = workspaces.signUp();
    workspaces.setPlan(w, "team");
    workspaces.endTrial(w);
    mockMvc.perform(get("/api/v1/packs/au-adm").header("Authorization", w.bearer()))
        .andExpect(status().isPaymentRequired());
  }
}
