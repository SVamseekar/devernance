package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

/** Production setting: a crosswalk nobody has reviewed against its source is not served anywhere. */
@SpringBootTest(properties = {
    "assurance.frameworks.serve-unreviewed=false",
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "assurance.app.base-url=https://app.test",
    "assurance.security.auth-rate.per-ip-per-15m=1000",
    "assurance.auth.email.cooldown-seconds=0",
    "spring.jackson.mapper.accept-case-insensitive-enums=true"
})
@AutoConfigureMockMvc
class FrameworkReviewGateApiTest {
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

  @Test
  void anUnreviewedCrosswalkIsNotLive() {
    Crosswalk unreviewed = new Crosswalk("nist-ai-rmf", "1.0", "src", "2026-10-05", "", List.of());
    Crosswalk reviewed = new Crosswalk("nist-ai-rmf", "1.0", "src", "2026-10-05", "MSV", List.of());
    assertThat(CrosswalkService.live(unreviewed, false)).isFalse();
    assertThat(CrosswalkService.live(reviewed, false)).isTrue();
    assertThat(CrosswalkService.live(unreviewed, true)).isTrue();
  }

  @Test
  void anUnreviewedFrameworkIsNotServedEvenOnAPlanThatIncludesIt() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    for (String framework : List.of("nist-ai-rmf", "iso-42001", "colorado-sb26-189")) {
      mockMvc.perform(get("/api/v1/systems/{id}/frameworks/{framework}", id, framework)
              .header("Authorization", w.bearer()))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void theSignedPackLeavesUnreviewedFrameworksOut() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    String pack = mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", id).header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(pack).has("frameworks")).isFalse();
  }

  @Test
  void theQuestionnaireDoesNotCiteAnUnreviewedNistMapping() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    workspaces.passControl(w, id, "HUMAN_OVERSIGHT");
    workspaces.indexDocument(w, id, "POLICY", "Oversight policy");
    JsonNode export = json.readTree(mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", id)
            .header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    for (JsonNode row : export.get("answers")) {
      if ("nist-alignment".equals(row.get("topic").asText())) {
        assertThat(row.get("answer").asText()).isEqualTo(QuestionnaireExportService.MANUAL);
        return;
      }
    }
    throw new AssertionError("nist-alignment missing");
  }
}
