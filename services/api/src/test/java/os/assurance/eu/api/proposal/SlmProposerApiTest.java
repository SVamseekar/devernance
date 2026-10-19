package os.assurance.eu.api.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "assurance.corpus.as-of=2026-09-23",
    "assurance.slm.map-endpoint="
})
@AutoConfigureMockMvc
class SlmProposerApiTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;
  @Autowired SlmMapClient slm;

  @AfterEach
  void resetSlm() {
    slm.setEndpointForTest("");
  }

  @Test
  void endpointDownStillFillsQueueFromRulesMapper() throws Exception {
    slm.setEndpointForTest("http://127.0.0.1:9/map");
    String systemId = createSystem();
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(fiveDocuments()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(5));
    mockMvc.perform(get("/api/v1/systems/{id}/proposals", systemId).with(officer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slmDown").value(true))
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.items[0].status").value("PENDING"))
        .andExpect(jsonPath("$.items[0].adapterVersion").isEmpty());
  }

  @Test
  void invalidJsonWritesNoRowAndAuditsSchemaInvalid() throws Exception {
    String body = serve("not-json");
    try {
      String systemId = createSystem();
      mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
              .with(officer())
              .contentType(MediaType.APPLICATION_JSON)
              .content(oneDocument()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.length()").value(0));
      mockMvc.perform(get("/api/v1/systems/{id}/proposals", systemId).with(officer()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(0));
      mockMvc.perform(get("/api/v1/audit-events").with(officer()).param("systemId", systemId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.eventType=='map.schema_invalid')].payload.reason").value("invalid_json"));
    } finally {
      body = null;
    }
  }

  @Test
  void abstainProposesNoLink() throws Exception {
    serve("{\"relation\":\"abstain\"}");
    String systemId = createSystem();
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(oneDocument()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].relation").value("abstain"))
        .andExpect(jsonPath("$[0].provisionKey").isEmpty());
  }

  @Test
  void candidateIdStaysPendingForAHuman() throws Exception {
    MvcResult corpus = mockMvc.perform(get("/api/v1/corpus").with(officer()))
        .andExpect(status().isOk())
        .andReturn();
    String provisionKey = objectMapper.readTree(corpus.getResponse().getContentAsString())
        .get("provisions").get(0).get("provisionKey").asText();
    serve("{\"relation\":\"supports\",\"obligation_id\":\"" + provisionKey + "\"}");
    String systemId = createSystem();
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(oneDocument()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].relation").value("supports"))
        .andExpect(jsonPath("$[0].provisionKey").value(provisionKey))
        .andExpect(jsonPath("$[0].adapterVersion").value(SlmMapClient.ADAPTER_VERSION));
    mockMvc.perform(get("/api/v1/systems/{id}/proposals", systemId).with(officer()))
        .andExpect(jsonPath("$.items[0].status").value("PENDING"));
  }

  @Test
  void failedCallSendsTheRestOfTheBatchToRulesAndRecoveryClearsTheDownFlag() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger statusCode = new AtomicInteger(503);
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext("/map", exchange -> {
      calls.incrementAndGet();
      byte[] payload = "{\"relation\":\"abstain\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(statusCode.get(), payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    });
    http.start();
    server.set(http);
    slm.setEndpointForTest("http://127.0.0.1:" + http.getAddress().getPort() + "/map");
    String systemId = createSystem();

    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(fiveDocuments()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(5));
    assertThat(calls.get()).isEqualTo(1);
    mockMvc.perform(get("/api/v1/systems/{id}/proposals", systemId).with(officer()))
        .andExpect(jsonPath("$.slmDown").value(true));

    statusCode.set(200);
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(oneDocument()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].relation").value("abstain"));
    mockMvc.perform(get("/api/v1/systems/{id}/proposals", systemId).with(officer()))
        .andExpect(jsonPath("$.slmDown").value(false));
  }

  @Test
  void oversizedBatchesAreRejectedBeforeAnyModelCall() throws Exception {
    String systemId = createSystem();
    String document = "{\"title\":\"Note\",\"text\":\"Short note.\"}";
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"documents\":[" + String.join(",", java.util.Collections.nCopies(21, document)) + "]}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/v1/systems/{id}/proposals/map", systemId)
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"documents\":[{\"title\":\"" + "t".repeat(301) + "\",\"text\":\"Short note.\"}]}"))
        .andExpect(status().isBadRequest());
  }

  private final AtomicReference<HttpServer> server = new AtomicReference<>();

  private String serve(String responseBody) throws Exception {
    HttpServer previous = server.getAndSet(null);
    if (previous != null) {
      previous.stop(0);
    }
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext("/map", exchange -> {
      byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    });
    http.start();
    server.set(http);
    slm.setEndpointForTest("http://127.0.0.1:" + http.getAddress().getPort() + "/map");
    return responseBody;
  }

  @AfterEach
  void stopServer() {
    HttpServer http = server.getAndSet(null);
    if (http != null) {
      http.stop(0);
    }
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

  private String oneDocument() {
    return """
        {"documents":[{"title":"Model card","text":"Model card for a credit-scoring assistant. The system is a high-risk AI system listed in Annex III."}]}
        """;
  }

  private String createSystem() throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/systems")
            .with(officer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"SLM proposer sitting","owner":"Compliance","purpose":"Map with a seam",
                 "riskClass":"LIMITED","riskBasis":"Limited transparency duties",
                 "deploymentRegion":"EU","evidenceCoverage":90,"evalScore":90,
                 "dataContractStatus":"HEALTHY","openGaps":[]}
                """))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private RequestPostProcessor officer() {
    String token = jwtService.issueAccessToken(
        TenantContext.DEFAULT_USER_ID, TenantContext.DEFAULT_TENANT_ID, UserRole.COMPLIANCE_OFFICER);
    return request -> {
      request.addHeader("Authorization", "Bearer " + token);
      return request;
    };
  }
}
