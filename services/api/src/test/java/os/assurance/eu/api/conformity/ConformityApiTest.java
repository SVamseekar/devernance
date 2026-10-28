package os.assurance.eu.api.conformity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import os.assurance.eu.api.tenant.ApiKeyEntity;
import os.assurance.eu.api.tenant.ApiKeyHasher;
import os.assurance.eu.api.tenant.ApiKeyJpaRepository;
import os.assurance.eu.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
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
    "spring.jackson.mapper.accept-case-insensitive-enums=true"
})
@AutoConfigureMockMvc
class ConformityApiTest {
  private static final String DEFAULT_API_KEY = "00000000-0000-0000-0000-000000000a01";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ApiKeyJpaRepository apiKeyRepo;

  @BeforeEach
  void seedApiKey() {
    UUID apiKeyId = UUID.fromString(DEFAULT_API_KEY);
    apiKeyRepo.findById(apiKeyId)
        .orElseGet(() -> apiKeyRepo.save(new ApiKeyEntity(
            apiKeyId,
            ApiKeyHasher.sha256Hex(DEFAULT_API_KEY),
            TenantContext.DEFAULT_TENANT_ID,
            TenantContext.DEFAULT_USER_ID,
            Instant.now())));
  }

  @Test
  void dossierStartsWithNineAnnexIvSectionsAndExportsInPack() throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/systems")
            .with(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Conformity Demo",
                  "owner": "Compliance",
                  "purpose": "Checklist coverage",
                  "riskClass": "HIGH",
                  "riskBasis": "Test",
                  "deploymentRegion": "EU",
                  "evidenceCoverage": 90,
                  "evalScore": 90,
                  "dataContractStatus": "HEALTHY",
                  "openGaps": []
                }
                """))
        .andExpect(status().isCreated())
        .andReturn();
    String systemId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(get("/api/v1/systems/{id}/conformity", systemId).with(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.disclaimer", org.hamcrest.Matchers.containsString("not a technical file")))
        .andExpect(jsonPath("$.annexIv.sections.length()").value(9))
        .andExpect(jsonPath("$.declarationOfConformity.status").value("NOT_ISSUED"))
        .andExpect(jsonPath("$.art49Registration.status").value("NOT_REGISTERED"));

    mockMvc.perform(get("/api/v1/systems/{id}/evgraph-artifacts", systemId).with(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model_card.model_name").value("Conformity Demo"))
        .andExpect(jsonPath("$.approval.approver").exists())
        .andExpect(jsonPath("$.howto", org.hamcrest.Matchers.containsString("evgraph")));

    mockMvc.perform(get("/api/v1/systems/{id}/evidence-pack", systemId).with(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.evidencePackVersion").value("1.2"))
        .andExpect(jsonPath("$.evgraphArtifacts.model_card.model_name").value("Conformity Demo"));
  }

  @Test
  void putUpdatesFriaStatus() throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/systems")
            .with(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "FRIA Demo",
                  "owner": "Legal",
                  "purpose": "FRIA record",
                  "riskClass": "LIMITED",
                  "riskBasis": "Test",
                  "deploymentRegion": "EU"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn();
    String systemId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(put("/api/v1/systems/{id}/conformity", systemId)
            .with(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fria": {
                    "disclaimer": "Assisted",
                    "status": "RECORDED",
                    "summary": "Internal FRIA note",
                    "evidenceRef": "doc-1",
                    "reviewer": "counsel"
                  }
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fria.status").value("RECORDED"));
  }

  private RequestPostProcessor authenticated() {
    return request -> {
      request.addHeader("X-Api-Key", DEFAULT_API_KEY);
      return request;
    };
  }
}
