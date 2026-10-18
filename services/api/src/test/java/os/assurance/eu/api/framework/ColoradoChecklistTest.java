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
class ColoradoChecklistTest {
  static final int DEPLOYER_DUTIES = 9;

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

  private static Crosswalk colorado() {
    return CrosswalkService.load(new ObjectMapper()).stream()
        .filter(cw -> "colorado-sb26-189".equals(cw.framework()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void theChecklistListsEachDeployerDutyWithItsSection() {
    Crosswalk cw = colorado();
    assertThat(cw.type()).isEqualTo("checklist");
    assertThat(cw.items()).hasSize(DEPLOYER_DUTIES);
    assertThat(cw.items()).allSatisfy(item -> {
      assertThat(item.id()).startsWith("SB26-189 §6-1-170");
      assertThat(item.sourceUrl()).startsWith("https://leg.colorado.gov/");
      assertThat(item.title().split(" ")).hasSizeLessThanOrEqualTo(25);
    });
    CrosswalkService.validate(cw, Set.copyOf(ControlService.baselineCodes()));
  }

  @Test
  void aTrialWorkspaceSeesTheChecklist() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Tenant screening", "high", "");
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/colorado-sb26-189", id).header("Authorization", w.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("checklist"))
        .andExpect(jsonPath("$.items.length()").value(DEPLOYER_DUTIES))
        .andExpect(jsonPath("$.items[0].sourceUrl").exists());
  }

  @Test
  void teamAndFreePlansGetPaymentRequired() throws Exception {
    var team = workspaces.signUp();
    String teamSystem = workspaces.createSystem(team, "Tenant screening", "high", "");
    workspaces.setPlan(team, "team");
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/colorado-sb26-189", teamSystem)
            .header("Authorization", team.bearer()))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.code").value("feature_us_state_packs"));
    // Team still has the NIST crosswalk.
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/nist-ai-rmf", teamSystem)
            .header("Authorization", team.bearer()))
        .andExpect(status().isOk());

    var free = workspaces.signUp();
    String freeSystem = workspaces.createSystem(free, "Tenant screening", "high", "");
    workspaces.endTrial(free);
    mockMvc.perform(get("/api/v1/systems/{id}/frameworks/colorado-sb26-189", freeSystem)
            .header("Authorization", free.bearer()))
        .andExpect(status().isPaymentRequired());
  }
}
