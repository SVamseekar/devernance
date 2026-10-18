package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import os.assurance.eu.api.control.ControlService;
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
class IsoCrosswalkTest {
  static final int ANNEX_A_CONTROLS = 38;

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

  private static Crosswalk iso() {
    return CrosswalkService.load(new ObjectMapper()).stream()
        .filter(cw -> "iso-42001".equals(cw.framework()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void usesAnnexAIdentifiersWithShortOwnParaphrasesOnly() {
    Crosswalk cw = iso();
    assertThat(cw.items()).hasSize(ANNEX_A_CONTROLS);
    assertThat(cw.source()).contains("identifiers only").contains("paraphrases");
    assertThat(cw.items()).allSatisfy(item -> {
      assertThat(item.id()).matches("A\\.\\d+(\\.\\d+)+");
      assertThat(item.title().split(" ")).hasSizeLessThanOrEqualTo(12);
    });
    assertThat(cw.reviewed()).isFalse();
    CrosswalkService.validate(cw, Set.copyOf(ControlService.baselineCodes()));
  }

  @Test
  void businessAndTrialSeeItTeamAndFreeDoNot() throws Exception {
    var trial = workspaces.signUp();
    String trialSystem = workspaces.createSystem(trial, "Claims triage", "high", "");
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/iso-42001", trialSystem).header("Authorization", trial.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(ANNEX_A_CONTROLS))
        .andExpect(jsonPath("$.reviewed").value(false));

    var team = workspaces.signUp();
    String teamSystem = workspaces.createSystem(team, "Claims triage", "high", "");
    workspaces.setPlan(team, "team");
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/iso-42001", teamSystem).header("Authorization", team.bearer()))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.code").value("feature_iso_crosswalk"));

    var free = workspaces.signUp();
    String freeSystem = workspaces.createSystem(free, "Claims triage", "high", "");
    workspaces.endTrial(free);
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/iso-42001", freeSystem).header("Authorization", free.bearer()))
        .andExpect(status().isPaymentRequired());
  }
}
