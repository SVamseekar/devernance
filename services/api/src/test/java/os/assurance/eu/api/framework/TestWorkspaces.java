package os.assurance.eu.api.framework;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import os.assurance.eu.api.email.EmailMessage;
import os.assurance.eu.api.email.EmailSender;
import os.assurance.eu.api.tenant.UserJpaRepository;

/** Self-serve workspaces for framework API tests: sign up, verify, then register systems. Starts on a trial. */
record TestWorkspaces(MockMvc mockMvc, ObjectMapper json, UserJpaRepository users, EmailSender emailSender,
    JdbcTemplate jdbc) {
  static final String PASSWORD = "correct-horse-battery";

  record Workspace(String bearer, UUID tenantId) {}

  Workspace signUp() throws Exception {
    String email = "fw-" + UUID.randomUUID() + "@acme.example";
    mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"organisationName\":\"Fw " + UUID.randomUUID() + "\"}"))
        .andExpect(status().isAccepted());
    ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailSender, atLeastOnce()).send(captor.capture());
    String body = captor.getAllValues().stream().filter(m -> email.equals(m.to()))
        .reduce((a, b) -> b).orElseThrow().textBody();
    String token = body.substring(body.indexOf("token=") + 6).split("\\s")[0];
    String verified = mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String bearer = "Bearer " + json.readTree(verified).get("accessToken").asText();
    return new Workspace(bearer, users.findByEmailIgnoreCase(email).orElseThrow().tenantId());
  }

  /** Registers a system from a JSON fragment of extra fields (may be empty) and returns its id. */
  String createSystem(Workspace w, String name, String riskClass, String extraJson) throws Exception {
    String extra = extraJson == null || extraJson.isBlank() ? "" : "," + extraJson;
    String created = mockMvc.perform(post("/api/v1/systems").header("Authorization", w.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"%s","owner":"Ops","purpose":"Prioritise incoming insurance claims.","riskClass":"%s",
                 "riskBasis":"fixture","deploymentRegion":"AU","evidenceCoverage":0,"evalScore":0,
                 "dataContractStatus":"warning","openGaps":[]%s}""".formatted(name, riskClass, extra)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(created).get("id").asText();
  }

  /** Marks one of the system's attached controls as passing, the way a reviewer does in the dashboard. */
  void passControl(Workspace w, String systemId, String controlCode) throws Exception {
    String listed = mockMvc.perform(get("/api/v1/systems/{id}/controls", systemId).header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String controlId = null;
    for (var control : json.readTree(listed)) {
      if (controlCode.equals(control.get("controlCode").asText())) {
        controlId = control.get("controlId").asText();
      }
    }
    if (controlId == null) {
      throw new AssertionError("Control " + controlCode + " is not attached to system " + systemId);
    }
    mockMvc.perform(put("/api/v1/systems/{id}/controls/{controlId}", systemId, controlId)
            .header("Authorization", w.bearer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PASS\",\"notes\":\"reviewed\"}"))
        .andExpect(status().isOk());
  }

  /** Ingests a small text document so it is indexed, and returns its id. */
  String indexDocument(Workspace w, String systemId, String type, String title) throws Exception {
    String created = mockMvc.perform(post("/api/v1/evidence/documents").header("Authorization", w.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of(
                "systemId", systemId, "type", type, "title", title,
                "sourceUri", "memory://" + UUID.randomUUID(),
                "content", "A written document for this system: " + title + "."))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(created).get("id").asText();
  }

  void endTrial(Workspace w) {
    jdbc.update("update tenants set trial_ends_at = ? where id = ?",
        Timestamp.from(Instant.now().minusSeconds(60)), w.tenantId());
  }

  void setPlan(Workspace w, String plan) {
    jdbc.update("update tenants set plan = ? where id = ?", plan, w.tenantId());
  }
}
