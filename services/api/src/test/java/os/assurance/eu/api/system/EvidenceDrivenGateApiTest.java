package os.assurance.eu.api.system;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "assurance.gate.manual-inputs=false"
})
@AutoConfigureMockMvc
class EvidenceDrivenGateApiTest {
  private static final UUID ENGINEER = UUID.fromString("00000000-0000-0000-0000-000000000102");

  @Autowired MockMvc mockMvc;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

  @Test
  void productionModeDerivesCoverageAndBlocksManualNumbers() throws Exception {
    mockMvc.perform(post("/api/v1/systems")
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(systemBody("HIGH", "\"evidenceCoverage\":100")))
        .andExpect(status().isBadRequest());

    String highId = create("HIGH", "");
    mockMvc.perform(get("/api/v1/systems/{id}/release-gate", highId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("BLOCKED"))
        .andExpect(jsonPath("$.blockers", hasItem(containsString("No completed eval run"))));

    mockMvc.perform(patch("/api/v1/systems/{id}", highId)
            .with(engineer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"openGaps\":[]}"))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/evidence/documents")
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"systemId":"%s","type":"POLICY","title":"Policy","sourceUri":"memory://policy","content":"A written policy for this system."}
                """.formatted(highId)))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/systems/{id}", highId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.evidenceCoverage").value(20))
        .andExpect(jsonPath("$.openGaps", hasItem("Missing evidence: DPIA")))
        .andExpect(jsonPath("$.openGaps", hasItem("Missing evidence: MODEL_CARD")))
        .andExpect(jsonPath("$.openGaps", hasItem("Missing evidence: CONTROL_MAP")))
        .andExpect(jsonPath("$.openGaps", hasItem("Missing evidence: VENDOR_DOC")));

    String limitedId = create("LIMITED", "");
    mockMvc.perform(get("/api/v1/systems/{id}/release-gate", limitedId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("REVIEW"))
        .andExpect(jsonPath("$.blockers").isEmpty())
        .andExpect(jsonPath("$.decision").value("REVIEW"));
    mockMvc.perform(get("/api/v1/systems/{id}", limitedId).with(officer()))
        .andExpect(jsonPath("$.openGaps", hasItem("No completed eval run")));
  }

  @Test
  void engineeringLeadCannotLowerTheRiskClassButCanRaiseIt() throws Exception {
    String highId = create("HIGH", "");
    mockMvc.perform(patch("/api/v1/systems/{id}", highId)
            .with(engineer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"riskClass\":\"MINIMAL\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/systems/{id}/risk-classification", highId)
            .with(engineer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"riskClass\":\"LIMITED\",\"basis\":\"Looks harmless\",\"humanOversightRequired\":false}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/systems/{id}", highId).with(officer()))
        .andExpect(jsonPath("$.riskClass").value("HIGH"));

    String limitedId = create("LIMITED", "");
    mockMvc.perform(patch("/api/v1/systems/{id}", limitedId)
            .with(engineer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"riskClass\":\"HIGH\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.riskClass").value("HIGH"));
  }

  @Test
  void aRiskClassChangeIsAuditedWithItsPreviousValue() throws Exception {
    String highId = create("HIGH", "");
    mockMvc.perform(patch("/api/v1/systems/{id}", highId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"riskClass\":\"MINIMAL\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/audit-events").param("systemId", highId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.eventType == 'ai_system.updated')].payload.previousRiskClass", hasItem("HIGH")))
        .andExpect(jsonPath("$[?(@.eventType == 'ai_system.updated')].payload.riskClass", hasItem("MINIMAL")));

    String otherId = create("HIGH", "");
    mockMvc.perform(post("/api/v1/systems/{id}/risk-classification", otherId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"riskClass\":\"LIMITED\",\"basis\":\"Reviewed: transparency duties only\",\"humanOversightRequired\":false}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/audit-events").param("systemId", otherId).with(officer()))
        .andExpect(jsonPath(
            "$[?(@.eventType == 'risk_classification.updated')].payload.previousRiskClass", hasItem("HIGH")));
  }

  private String create(String riskClass, String extra) throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/systems")
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(systemBody(riskClass, extra)))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private static String systemBody(String riskClass, String extra) {
    return """
        {"name":"Evidence driven","owner":"Ops","purpose":"Gate from evidence",
         "riskClass":"%s","riskBasis":"Test basis","deploymentRegion":"EU"%s}
        """.formatted(riskClass, extra.isBlank() ? "" : "," + extra);
  }

  private RequestPostProcessor officer() {
    return bearer(TenantContext.DEFAULT_USER_ID, UserRole.COMPLIANCE_OFFICER);
  }

  private RequestPostProcessor engineer() {
    return bearer(ENGINEER, UserRole.AI_ENGINEERING_LEAD);
  }

  private RequestPostProcessor bearer(UUID userId, UserRole role) {
    String token = jwtService.issueAccessToken(userId, TenantContext.DEFAULT_TENANT_ID, role);
    return request -> {
      request.addHeader("Authorization", "Bearer " + token);
      return request;
    };
  }
}
