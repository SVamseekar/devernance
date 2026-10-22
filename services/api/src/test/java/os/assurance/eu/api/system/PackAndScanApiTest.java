package os.assurance.eu.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "assurance.corpus.as-of=2026-09-23"
})
@AutoConfigureMockMvc
class PackAndScanApiTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

  @Test
  void packAndEvgraphScanReportTheSameCurrentGaps() throws Exception {
    String systemId = createSystem(90);
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(fiveDocuments()))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/api/v1/systems/{id}/proposals", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"relation\":\"abstain\",\"excerpt\":\"No statute in this note.\"}"))
        .andExpect(status().isCreated());

    JsonNode items = listProposals(systemId);
    String annexId = null;
    String rejectId = null;
    boolean sawPending = false;
    for (JsonNode item : items) {
      String relation = item.get("relation").asText();
      String status = item.get("status").asText();
      if ("abstain".equals(relation)) {
        continue;
      }
      if (annexId == null && "FUTURE".equals(text(item, "forceStatus"))
          && "2027-12-02".equals(text(item, "forceFrom"))) {
        annexId = item.get("id").asText();
        continue;
      }
      if (rejectId == null && "PENDING".equals(status)) {
        rejectId = item.get("id").asText();
        continue;
      }
      if ("PENDING".equals(status)) {
        sawPending = true;
      }
    }
    assertThat(annexId).isNotBlank();
    assertThat(rejectId).isNotBlank();
    assertThat(sawPending).isTrue();

    mockMvc.perform(post("/api/v1/systems/{id}/proposals/{proposalId}/accept", systemId, annexId)
            .with(officer()))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/{proposalId}/reject", systemId, rejectId)
            .with(officer()))
        .andExpect(status().isOk());

    MvcResult packed = mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", systemId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.corpusVersion").isNotEmpty())
        .andReturn();
    JsonNode pack = objectMapper.readTree(packed.getResponse().getContentAsString());
    String corpusVersion = pack.get("corpusVersion").asText();
    JsonNode corpus = objectMapper.readTree(mockMvc.perform(get("/api/v1/corpus").with(officer()))
        .andReturn()
        .getResponse()
        .getContentAsString());
    assertThat(corpusVersion).isEqualTo(corpus.get("corpusVersion").asText());

    JsonNode accepted = pack.get("acceptedLinks");
    assertThat(accepted).isNotNull();
    assertThat(accepted).hasSize(1);
    assertThat(accepted.get(0).get("status").asText()).isEqualTo("ACCEPTED");
    assertThat(accepted.get(0).get("forceStatus").asText()).isEqualTo("FUTURE");
    assertThat(accepted.get(0).get("forceFrom").asText()).isEqualTo("2027-12-02");

    List<String> queueLabels = new ArrayList<>();
    for (JsonNode row : pack.get("queue")) {
      queueLabels.add(row.get("label").asText());
      assertThat(row.get("status").asText()).isNotEqualTo("ACCEPTED");
    }
    assertThat(queueLabels).contains("PENDING", "REJECTED", "abstain");

    JsonNode artifacts = pack.get("acceptedArtifacts");
    assertThat(artifacts.get("approval").has("approved_at")).isFalse();
    // No stage was approved, so nobody is named: the pack never invents an approver.
    assertThat(artifacts.get("approval").get("approver").asText()).isEmpty();
    assertThat(artifacts.get("model_card").get("model_name").asText()).isNotBlank();
    // Devernance records no deployment, so the pack states no deployment time (editing the system is not one).
    assertThat(artifacts.get("deployment").has("deployed_at")).isFalse();
    // The system lists no data sources, so there is no dataset manifest to scan, and none is made up.
    assertThat(artifacts.has("dataset_manifest_csv")).isFalse();

    List<String> packGaps = gapKeys(pack.get("currentGaps"));
    assertThat(packGaps).contains("approval-precedes-deployment|INCONCLUSIVE");
    assertThat(packGaps).noneMatch(gap -> gap.toLowerCase(Locale.ROOT).contains("annex"));
    assertThat(packGaps).noneMatch(gap -> gap.contains("FUTURE") || gap.contains("PENDING") || gap.contains("REJECTED"));

    assertThat(packGaps).noneMatch(gap -> gap.startsWith("dataset-manifest-complete"));
    List<String> cliGaps = scanExportedArtifacts(artifacts);
    assertThat(packGaps).containsExactlyInAnyOrderElementsOf(cliGaps);

    mockMvc.perform(patch("/api/v1/systems/{id}", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"evalScore\":70}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/systems/{id}/release-gate", systemId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("BLOCKED"))
        .andExpect(jsonPath("$.blockers[?(@ == 'Eval score is below hard release threshold')]").exists())
        .andExpect(jsonPath("$.blockers[?(@ =~ /.*[Aa]nnex III.*/)]").isEmpty());
    String body = mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", systemId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("BLOCKED"))
        .andReturn()
        .getResponse()
        .getContentAsString()
        .toLowerCase(Locale.ROOT);
    assertThat(body).doesNotContain("compliant");
    assertThat(body).doesNotContain("certified");
    assertThat(body).doesNotContain("conformity");
  }

  @Test
  void declaredDataSourcesBecomeTheDatasetManifestWithoutInventedLicenses() throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/systems")
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Data source pack","owner":"Reviewer","purpose":"List data sources",
                 "riskClass":"LIMITED","riskBasis":"Limited transparency duties",
                 "deploymentRegion":"EU","dataContractStatus":"HEALTHY","openGaps":[],
                 "dataSources":["CRM export","Vendor \\"scores\\", v2","CRM export","  "]}
                """))
        .andExpect(status().isCreated())
        .andReturn();
    String systemId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    JsonNode pack = objectMapper.readTree(mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", systemId)
            .with(officer()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString());
    JsonNode artifacts = pack.get("acceptedArtifacts");
    String csv = artifacts.get("dataset_manifest_csv").asText();
    assertThat(csv).doesNotContain("ds-accepted").doesNotContain("Apache-2.0");
    assertThat(csv.lines().toList()).containsExactly(
        "dataset_id,source,license,contains_pii",
        "CRM export,ai_system.dataSources,,",
        "\"Vendor \"\"scores\"\", v2\",ai_system.dataSources,,");
    assertThat(pack.get("evgraphArtifacts").get("dataset_manifest_csv").asText()).isEqualTo(csv);

    List<String> packGaps = gapKeys(pack.get("currentGaps"));
    assertThat(packGaps).filteredOn(gap -> gap.startsWith("dataset-manifest-complete"))
        .containsExactly(
            "dataset-manifest-complete|EXPECTATION_NOT_MET",
            "dataset-manifest-complete|EXPECTATION_NOT_MET");
    assertThat(packGaps).containsExactlyInAnyOrderElementsOf(scanExportedArtifacts(artifacts));
  }

  private List<String> scanExportedArtifacts(JsonNode artifacts) throws Exception {
    Path dir = Files.createTempDirectory("pack-scan");
    Path modelCard = dir.resolve("model_card.json");
    Path approval = dir.resolve("approval.json");
    Path deployment = dir.resolve("deployment.json");
    Path dataset = dir.resolve("dataset_manifest.csv");
    Files.writeString(modelCard, artifacts.get("model_card").toString());
    Files.writeString(approval, artifacts.get("approval").toString());
    Files.writeString(deployment, artifacts.get("deployment").toString());
    List<String> gaps = new ArrayList<>();
    gaps.addAll(cliGaps("scan", modelCard.toString(), approval.toString(), deployment.toString()));
    if (artifacts.has("dataset_manifest_csv")) {
      Files.writeString(dataset, artifacts.get("dataset_manifest_csv").asText());
      gaps.addAll(cliGaps("scan-dataset-manifest", dataset.toString()));
    }
    return gaps;
  }

  private List<String> cliGaps(String command, String... args) throws Exception {
    List<String> cmd = new ArrayList<>();
    cmd.add(EvgraphCli.resolveCommand("evgraph", true));
    cmd.add(command);
    cmd.addAll(List.of(args));
    cmd.add("--format");
    cmd.add("json");
    Process process = new ProcessBuilder(cmd).start();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    int code = process.waitFor();
    assertThat(code).as(stderr).isZero();
    JsonNode report = objectMapper.readTree(stdout);
    return gapKeys(report.get("findings"));
  }

  private static List<String> gapKeys(JsonNode findings) {
    List<String> gaps = new ArrayList<>();
    if (findings == null || !findings.isArray()) {
      return gaps;
    }
    for (JsonNode finding : findings) {
      String outcome = finding.has("outcome")
          ? finding.get("outcome").asText()
          : text(finding, "outcome");
      if ("EXPECTATION_MET".equals(outcome) || "expectation_met".equals(outcome)) {
        continue;
      }
      String rule = finding.has("rule_id")
          ? finding.get("rule_id").asText()
          : finding.get("ruleId").asText();
      gaps.add(rule + "|" + outcome);
    }
    return gaps;
  }

  private JsonNode listProposals(String systemId) throws Exception {
    MvcResult listed = mockMvc.perform(get("/api/v1/systems/{id}/proposals", systemId).with(officer()))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(listed.getResponse().getContentAsString()).get("items");
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText();
  }

  private String fiveDocuments() {
    return """
        {"documents":[
          {"title":"Model card","text":"Model card for a credit-scoring assistant. The system is a high-risk AI system listed in Annex III."},
          {"title":"Approval note","text":"Approval note: natural persons are informed that they interact with an AI system before the release."},
          {"title":"Dataset note","text":"Dataset note. Training rows are personal data and shall be processed lawfully, fairly and in a transparent manner."},
          {"title":"Eval summary","text":"Eval summary for security of network and information systems supporting the business processes of financial entities. Score 91."},
          {"title":"Retention policy","text":"Retention policy. Personal data shall be processed lawfully and kept only for the stated retention window."}
        ]}
        """;
  }

  private String createSystem(int evalScore) throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/systems")
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Pack scan sitting","owner":"Reviewer","purpose":"Export accepted files",
                 "riskClass":"LIMITED","riskBasis":"Limited transparency duties",
                 "deploymentRegion":"EU","evidenceCoverage":90,"evalScore":%d,
                 "dataContractStatus":"HEALTHY","openGaps":[]}
                """.formatted(evalScore)))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private RequestPostProcessor officer() {
    String token = jwtService.issueAccessToken(
        TenantContext.DEFAULT_USER_ID,
        TenantContext.DEFAULT_TENANT_ID,
        UserRole.COMPLIANCE_OFFICER);
    return request -> {
      request.addHeader("Authorization", "Bearer " + token);
      return request;
    };
  }
}
