package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
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
class QuestionnaireExportApiTest {
  static final String MANUAL = "Not recorded in Assurance OS — answer manually.";

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

  private JsonNode export(TestWorkspaces.Workspace w, String systemId) throws Exception {
    return json.readTree(mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", systemId)
            .header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private static JsonNode answer(JsonNode export, String topic) {
    for (JsonNode row : export.get("answers")) {
      if (topic.equals(row.get("topic").asText())) {
        return row;
      }
    }
    throw new AssertionError("No answer for topic " + topic);
  }

  @Test
  void oversightIsAnsweredFromThePassingControlAndTheIndexedPolicy() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    workspaces.passControl(w, id, "HUMAN_OVERSIGHT");
    String documentId = workspaces.indexDocument(w, id, "POLICY", "Human oversight policy v3");

    JsonNode export = export(w, id);
    assertThat(export.get("questionnaire").asText()).isEqualTo("ai-vendor-core");
    assertThat(export.get("systemId").asText()).isEqualTo(id);
    assertThat(export.get("disclaimer").asText()).contains("Review every answer");
    assertThat(export.get("answers")).hasSize(25);

    JsonNode oversight = answer(export, "human-oversight");
    assertThat(oversight.get("status").asText()).isEqualTo("answered");
    assertThat(oversight.get("answer").asText())
        .contains("Human oversight policy v3")
        .contains(LocalDate.now(ZoneOffset.UTC).toString());
    JsonNode document = null;
    JsonNode control = null;
    for (JsonNode ref : oversight.get("evidence")) {
      if ("document".equals(ref.get("kind").asText())) {
        document = ref;
      }
      if ("control".equals(ref.get("kind").asText())) {
        control = ref;
      }
    }
    assertThat(control.get("ref").asText()).isEqualTo("HUMAN_OVERSIGHT");
    assertThat(control.get("status").asText()).isEqualTo("PASS");
    assertThat(document.get("id").asText()).isEqualTo(documentId);
    assertThat(document.get("title").asText()).isEqualTo("Human oversight policy v3");
    assertThat(document.get("sha256").asText()).hasSize(64);
    assertThat(export.get("answered").asInt() + export.get("manual").asInt()).isEqualTo(25);
  }

  @Test
  void aSystemWithoutTheRecordsGetsTheManualTextAndTheMissingFacts() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    JsonNode oversight = answer(export(w, id), "human-oversight");
    assertThat(oversight.get("status").asText()).isEqualTo("manual");
    assertThat(oversight.get("answer").asText()).isEqualTo(MANUAL);
    assertThat(oversight.get("missing")).extracting(JsonNode::asText)
        .containsExactly("control.HUMAN_OVERSIGHT.passedOn", "evidence.POLICY.titles");
    // A control that is attached but not passing is shown with its real status, never as passing.
    assertThat(oversight.get("evidence").get(0).get("status").asText()).isNotEqualTo("PASS");
  }

  @Test
  void recordedSystemFactsAreUsedAndNothingElseIsInvented() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high",
        "\"vendorName\":\"Acme AI\",\"modelName\":\"triage-net\",\"modelVersion\":\"2.4\"");
    JsonNode export = export(w, id);
    assertThat(answer(export, "model-provenance").get("answer").asText())
        .contains("triage-net").contains("2.4").contains("Acme AI");
    assertThat(answer(export, "eu-ai-act-risk-class").get("answer").asText()).contains("HIGH").contains("fixture");
    assertThat(answer(export, "incident-response").get("answer").asText()).isEqualTo(MANUAL);
    assertThat(answer(export, "eval-coverage").get("answer").asText()).isEqualTo(MANUAL);
  }

  @Test
  void csvDownloadsWithOneRowPerQuestion() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims, \\\"triage\\\"", "high", "");
    byte[] body = mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", id).param("format", "csv")
            .header("Authorization", w.bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(header().string("Content-Disposition",
            "attachment; filename=\"questionnaire-ai-vendor-core-" + id + ".csv\""))
        .andReturn().getResponse().getContentAsByteArray();
    String csv = new String(body, StandardCharsets.UTF_8);
    assertThat(csv).startsWith("﻿id,topic,question,answer,status,evidence\r\n");
    assertThat(csv).contains("Not recorded in Assurance OS — answer manually.");
    // The system name carries a comma and quotes; the overview answer must stay one quoted cell.
    assertThat(csv).contains("\"Claims, \"\"triage\"\" is owned by Ops.");
    assertThat(csv.split("\r\n")).hasSize(26);
  }

  @Test
  void anUnknownFormatIsABadRequest() throws Exception {
    var w = workspaces.signUp();
    String id = workspaces.createSystem(w, "Claims triage", "high", "");
    mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", id).param("format", "xlsx")
            .header("Authorization", w.bearer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void teamAndFreePlansGetPaymentRequired() throws Exception {
    var team = workspaces.signUp();
    String teamSystem = workspaces.createSystem(team, "Claims triage", "high", "");
    workspaces.setPlan(team, "team");
    mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", teamSystem)
            .header("Authorization", team.bearer()))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.code").value("feature_questionnaire_export"));

    var free = workspaces.signUp();
    String freeSystem = workspaces.createSystem(free, "Claims triage", "high", "");
    workspaces.endTrial(free);
    mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", freeSystem).param("format", "csv")
            .header("Authorization", free.bearer()))
        .andExpect(status().isPaymentRequired());
  }

  @Test
  void anotherWorkspacesSystemIsNotFound() throws Exception {
    var owner = workspaces.signUp();
    String id = workspaces.createSystem(owner, "Claims triage", "high", "");
    var other = workspaces.signUp();
    mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", id).header("Authorization", other.bearer()))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/systems/{id}/questionnaire-export", UUID.randomUUID())
            .header("Authorization", owner.bearer()))
        .andExpect(status().isNotFound());
  }
}
