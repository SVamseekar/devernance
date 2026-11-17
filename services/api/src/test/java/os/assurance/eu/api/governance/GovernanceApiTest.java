package os.assurance.eu.api.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.tenant.ApiKeyEntity;
import os.assurance.eu.api.tenant.ApiKeyHasher;
import os.assurance.eu.api.tenant.ApiKeyJpaRepository;
import os.assurance.eu.api.tenant.ApiKeyScope;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;

/**
 * The governance runtime end to end, over HTTP: the insurance claims agent from docs/GOVERNANCE_PLATFORM.md.
 * Each test applies its own system (a unique name), so tests do not depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GovernanceApiTest {
  private static final UUID TENANT = TenantContext.DEFAULT_TENANT_ID;
  private static final UUID COMPLIANCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID ENGINEER = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID AUDITOR = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000105");
  private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-0000000c0001");
  private static final UUID OTHER_ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000c0101");
  private static final String MODEL = "gpt-4.1@2025-04-14";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper json;
  @Autowired JwtService jwt;
  @Autowired UserJpaRepository users;
  @Autowired TenantJpaRepository tenants;
  @Autowired ApiKeyJpaRepository apiKeys;

  @BeforeEach
  void seedSecondWorkspace() {
    Instant now = Instant.now();
    if (tenants.findById(OTHER_TENANT).isEmpty()) {
      tenants.save(new TenantEntity(OTHER_TENANT, "Governance Other Tenant", "starter", "EU", now));
    }
    if (users.findById(OTHER_ADMIN).isEmpty()) {
      users.save(new UserEntity(OTHER_ADMIN, OTHER_TENANT, "gov-other@example.com", UserRole.ADMIN, now));
    }
  }

  // ---- helpers ------------------------------------------------------------------------------------------

  private String bearer(UUID user, UserRole role) {
    return "Bearer " + jwt.issueAccessToken(user, TENANT, role);
  }

  private String admin() {
    return bearer(ADMIN, UserRole.ADMIN);
  }

  private String engineer() {
    return bearer(ENGINEER, UserRole.AI_ENGINEERING_LEAD);
  }

  private String compliance() {
    return bearer(COMPLIANCE, UserRole.COMPLIANCE_OFFICER);
  }

  private String auditor() {
    return bearer(AUDITOR, UserRole.AUDITOR);
  }

  private String otherAdmin() {
    return "Bearer " + jwt.issueAccessToken(OTHER_ADMIN, OTHER_TENANT, UserRole.ADMIN);
  }

  /** A real API key of the given scope, owned by the engineer, named like the workload that holds it. */
  private String apiKey(String name, ApiKeyScope scope) {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String raw = "dvn_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    apiKeys.save(new ApiKeyEntity(UUID.randomUUID(), ApiKeyHasher.sha256Hex(raw), TENANT, ENGINEER, Instant.now(),
        name, raw.substring(0, 12), scope));
    return raw;
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static String systemManifest(String name, String modelVersion, String tools) {
    return """
        {"apiVersion": "devernance.io/v1", "kind": "AISystem", "metadata": {"name": "%s"},
         "spec": {"owner": "Claims Engineering", "purpose": "Reads claims and proposes refunds",
                  "riskClass": "limited", "riskBasis": "Art. 50 transparency", "deploymentRegion": "EU",
                  "sector": "insurance",
                  "model": {"provider": "openai", "name": "gpt-4.1", "version": "%s"},
                  "promptHash": "sha256:prompt-1",
                  "tools": %s}}""".formatted(name, modelVersion, tools);
  }

  private static final String TOOLS = "[\"claim_search\", \"customer_database\", \"process_refund\"]";

  private static String policyManifest(String name, String system, String enforcement, String extraTools) {
    return """
        {"apiVersion": "devernance.io/v1", "kind": "GovernancePolicy", "metadata": {"name": "%s"},
         "spec": {
           "target": {"system": "%s"},
           "models": {"approved": ["gpt-4.1@2025-04-14"]},
           "tools": {"claim_search": "claim.search", "customer_database": "customer.read",
                     "process_refund": "payment.refund"%s},
           "rules": [
             {"action": "claim.search", "decision": "allow"},
             {"action": "customer.read", "decision": "allow",
              "requirement": "The agent reads only the customer of its authorised claim",
              "require": [{"field": "resource.customer_id", "op": "eq",
                           "valueFrom": "context.authorized_customer_id"}]},
             {"action": "payment.refund", "decision": "allow",
              "requirement": "Refunds above 50,000 need human approval",
              "review": [{"when": [{"field": "context.amount", "op": "gt", "value": 50000}],
                          "approverRoles": ["COMPLIANCE_OFFICER"], "reason": "High-value refund"}]},
             {"action": "customer.export", "decision": "deny",
              "reason": "External customer-data export is prohibited"}],
           "enforcement": %s}}""".formatted(name, system, extraTools, enforcement);
  }

  private static final String ENFORCE = "{\"deployment\": \"blocking\", \"runtime\": \"enforce\"}";

  private ResultActions postJson(String path, String auth, String body) throws Exception {
    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    request = auth.startsWith("Bearer ") ? request.header("Authorization", auth) : request.header("X-Api-Key", auth);
    return mockMvc.perform(request);
  }

  private ResultActions getAs(String path, String auth) throws Exception {
    var request = get(path);
    request = auth.startsWith("Bearer ") ? request.header("Authorization", auth) : request.header("X-Api-Key", auth);
    return mockMvc.perform(request);
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private JsonNode apply(String auth, String... manifests) throws Exception {
    return body(postJson("/api/v1/governance/apply", auth,
        "{\"resources\": [" + String.join(",", manifests) + "]}").andExpect(status().isOk()));
  }

  /** Applies the claims system and its policy; returns the system name. */
  private String claimsAgent() throws Exception {
    String name = unique("claims-agent");
    apply(admin(), systemManifest(name, "2025-04-14", TOOLS), policyManifest(unique("claims-policy"), name, ENFORCE, ""));
    return name;
  }

  private static String refund(String system, String customer, Number amount) {
    return """
        {"system": "%s", "action": "payment.refund", "model": "%s",
         "resource": {"type": "customer_payment", "customer_id": "%s"},
         "context": {"amount": %s, "currency": "INR"}}""".formatted(system, MODEL, customer, amount);
  }

  private JsonNode authorize(String auth, String request) throws Exception {
    return body(postJson("/api/v1/governance/authorize", auth, request).andExpect(status().isOk()));
  }

  private static String sha256(String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  // ---- define -------------------------------------------------------------------------------------------

  @Test
  void applyRegistersTheSystemAndIsDeclarative() throws Exception {
    String name = unique("claims-agent");
    String policy = unique("claims-policy");
    JsonNode first = apply(admin(), policyManifest(policy, name, ENFORCE, ""), systemManifest(name, "2025-04-14", TOOLS));
    // The system is applied first even though the policy came first in the request.
    assertThat(first.get("resources").get(0).get("kind").asText()).isEqualTo("AISystem");
    assertThat(first.get("resources").get(0).get("result").asText()).isEqualTo("created");
    assertThat(first.get("resources").get(1).get("result").asText()).isEqualTo("created");
    assertThat(first.get("resources").get(1).get("version").asInt()).isEqualTo(1);
    String systemId = first.get("resources").get(0).get("systemId").asText();

    JsonNode registered = body(getAs("/api/v1/systems/" + systemId, admin()).andExpect(status().isOk()));
    assertThat(registered.get("name").asText()).isEqualTo(name);
    assertThat(registered.get("modelName").asText()).isEqualTo("gpt-4.1");
    assertThat(registered.get("modelVersion").asText()).isEqualTo("2025-04-14");

    JsonNode again = apply(admin(), systemManifest(name, "2025-04-14", TOOLS), policyManifest(policy, name, ENFORCE, ""));
    assertThat(again.get("resources").get(0).get("result").asText()).isEqualTo("unchanged");
    assertThat(again.get("resources").get(1).get("result").asText()).isEqualTo("unchanged");

    JsonNode changed = apply(admin(), policyManifest(policy, name,
        "{\"deployment\": \"advisory\", \"runtime\": \"enforce\"}", ""));
    assertThat(changed.get("resources").get(0).get("result").asText()).isEqualTo("configured");
    assertThat(changed.get("resources").get(0).get("version").asInt()).isEqualTo(2);

    JsonNode active = body(getAs("/api/v1/governance/resources?system=" + name, admin()).andExpect(status().isOk()));
    assertThat(active).hasSize(2);
    JsonNode all = body(getAs("/api/v1/governance/resources?system=" + name + "&activeOnly=false", admin()));
    assertThat(all).hasSize(3);

    // The registry follows the manifest: a new model version updates the registered system.
    apply(admin(), systemManifest(name, "2025-09-01", TOOLS));
    assertThat(body(getAs("/api/v1/systems/" + systemId, admin())).get("modelVersion").asText())
        .isEqualTo("2025-09-01");
  }

  @Test
  void applyRejectsInvalidManifestsAsAWholeAndSaysWhatIsWrong() throws Exception {
    String name = unique("claims-agent");
    String badPolicy = policyManifest(unique("p"), name, ENFORCE, "").replace("\"decision\": \"deny\"", "\"decison\": \"deny\"");
    String message = body(postJson("/api/v1/governance/apply", admin(),
        "{\"resources\": [" + systemManifest(name, "2025-04-14", TOOLS) + "," + badPolicy + "]}")
        .andExpect(status().isBadRequest())).get("message").asText();
    assertThat(message).contains("unknown key 'decison'").contains("decision: is required");
    // Nothing was applied: the valid system in the same request was not registered either.
    getAs("/api/v1/governance/resources?system=" + name, admin()).andExpect(status().isNotFound());

    postJson("/api/v1/governance/apply", admin(),
        "{\"resources\": [" + policyManifest(unique("p"), "no-such-system", ENFORCE, "") + "]}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlyEditorsMayApply() throws Exception {
    String manifest = "{\"resources\": [" + systemManifest(unique("claims-agent"), "2025-04-14", TOOLS) + "]}";
    postJson("/api/v1/governance/apply", auditor(), manifest).andExpect(status().isForbidden());
    postJson("/api/v1/governance/apply", apiKey("ci", ApiKeyScope.RELEASE_GATE), manifest)
        .andExpect(status().isForbidden());
    postJson("/api/v1/governance/apply", apiKey("claims-agent", ApiKeyScope.RUNTIME), manifest)
        .andExpect(status().isForbidden());
  }

  // ---- enforce: the three situations ---------------------------------------------------------------------

  @Test
  void aSmallRefundIsAllowedVerifiedOnceAndItsOutcomeRecordedOnce() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    JsonNode decision = authorize(agentKey, refund(system, "CUST-102", 20000));
    assertThat(decision.get("status").asText()).isEqualTo("ALLOW");
    assertThat(decision.get("authorized").asBoolean()).isTrue();
    assertThat(decision.get("agent").asText()).isEqualTo("claims-agent");
    assertThat(decision.get("policy").get("version").asInt()).isEqualTo(1);
    assertThat(decision.get("approval").isNull()).isTrue();
    String id = decision.get("id").asText();
    String token = decision.get("authorizationToken").asText();

    // The payment service checks the token against exactly the request it is about to carry out, on the system
    // it protects.
    String verify = """
        {"token": "%s", "system": "%s", "action": "payment.refund",
         "resource": {"customer_id": "CUST-102", "type": "customer_payment"},
         "context": {"currency": "INR", "amount": 20000.0}}""".formatted(token, system);
    // A different amount is a different request.
    assertThat(body(postJson("/api/v1/governance/authorizations/verify", agentKey,
        verify.replace("20000.0", "49000"))).get("valid").asBoolean()).isFalse();
    // The same amount sent as a string is a different request too: the policy judged a number.
    assertThat(body(postJson("/api/v1/governance/authorizations/verify", agentKey,
        verify.replace("20000.0", "\"20000\""))).get("valid").asBoolean()).isFalse();
    assertThat(body(postJson("/api/v1/governance/authorizations/verify", agentKey,
        verify.replace(token, token.substring(0, token.length() - 4) + "AAAA"))).get("valid").asBoolean()).isFalse();
    // Another workspace cannot use it.
    assertThat(body(postJson("/api/v1/governance/authorizations/verify", otherAdmin(), verify))
        .get("valid").asBoolean()).isFalse();
    // Nor can a service that protects another system: its policy did not decide this.
    String otherSystem = claimsAgent();
    JsonNode wrongSystem = body(postJson("/api/v1/governance/authorizations/verify", agentKey,
        verify.replace("\"system\": \"" + system + "\"", "\"system\": \"" + otherSystem + "\"")));
    assertThat(wrongSystem.get("valid").asBoolean()).isFalse();
    assertThat(wrongSystem.get("reason").asText()).contains("not issued for this system");
    postJson("/api/v1/governance/authorizations/verify", agentKey, verify.replace("\"system\": \"" + system + "\",", ""))
        .andExpect(status().isBadRequest());

    JsonNode verified = body(postJson("/api/v1/governance/authorizations/verify", agentKey, verify));
    assertThat(verified.get("valid").asBoolean()).as(verified.toString()).isTrue();
    // The first successful check redeems the token: replaying it fails even before the outcome is recorded.
    JsonNode replayed = body(postJson("/api/v1/governance/authorizations/verify", agentKey, verify));
    assertThat(replayed.get("valid").asBoolean()).isFalse();
    assertThat(replayed.get("reason").asText()).contains("already used");
    assertThat(body(getAs("/api/v1/governance/decisions/" + id, agentKey)).get("authorizationToken").isNull()).isTrue();

    // Only the requester records the outcome, so nobody else can claim the refund ran.
    for (String other : List.of(apiKey("other-agent", ApiKeyScope.RUNTIME), engineer(), admin())) {
      postJson("/api/v1/governance/decisions/" + id + "/execution", other, "{\"status\": \"EXECUTED\"}")
          .andExpect(status().isForbidden());
    }
    JsonNode executed = body(postJson("/api/v1/governance/decisions/" + id + "/execution", agentKey,
        "{\"status\": \"EXECUTED\", \"reference\": \"payment-1001\"}").andExpect(status().isOk()));
    assertThat(executed.get("execution").get("status").asText()).isEqualTo("EXECUTED");
    assertThat(executed.get("execution").get("reference").asText()).isEqualTo("payment-1001");

    postJson("/api/v1/governance/decisions/" + id + "/execution", agentKey, "{\"status\": \"EXECUTED\"}")
        .andExpect(status().isConflict());
    JsonNode reused = body(postJson("/api/v1/governance/authorizations/verify", agentKey, verify));
    assertThat(reused.get("valid").asBoolean()).isFalse();
    assertThat(reused.get("reason").asText()).contains("already used");
    // Once used, no new token is issued for the decision.
    assertThat(body(getAs("/api/v1/governance/decisions/" + id, agentKey)).get("authorizationToken").isNull()).isTrue();
  }

  @Test
  void aLargeRefundWaitsForAnApproverWithThePolicyRole() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    JsonNode decision = authorize(agentKey, refund(system, "CUST-102", 75000));
    assertThat(decision.get("status").asText()).isEqualTo("REVIEW");
    assertThat(decision.get("authorized").asBoolean()).isFalse();
    assertThat(decision.get("authorizationToken").isNull()).isTrue();
    assertThat(decision.get("approval").get("status").asText()).isEqualTo("PENDING");
    assertThat(decision.get("reason").asText()).isEqualTo("High-value refund");
    String id = decision.get("id").asText();
    String approvalId = decision.get("approval").get("id").asText();

    // Not approved yet: the outcome cannot be reported as executed.
    postJson("/api/v1/governance/decisions/" + id + "/execution", agentKey, "{\"status\": \"EXECUTED\"}")
        .andExpect(status().isConflict());

    JsonNode pending = body(getAs("/api/v1/governance/approvals?status=PENDING", compliance()));
    assertThat(pending.findValuesAsText("id")).contains(approvalId);

    String approve = "/api/v1/governance/approvals/" + approvalId + "/approve";
    // The agent's own credential, any API key, and a role the policy did not name are all refused.
    postJson(approve, agentKey, "{}").andExpect(status().isForbidden());
    postJson(approve, apiKey("full", ApiKeyScope.FULL), "{}").andExpect(status().isForbidden());
    postJson(approve, engineer(), "{}").andExpect(status().isForbidden());
    postJson(approve, admin(), "{}").andExpect(status().isForbidden());

    JsonNode approved = body(postJson(approve, compliance(), "{\"rationale\": \"Claim documents checked\"}")
        .andExpect(status().isOk()));
    assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
    assertThat(approved.get("decidedBy").asText()).isEqualTo(COMPLIANCE.toString());
    postJson(approve, compliance(), "{}").andExpect(status().isConflict());

    JsonNode after = body(getAs("/api/v1/governance/decisions/" + id, agentKey));
    assertThat(after.get("status").asText()).isEqualTo("REVIEW");
    assertThat(after.get("authorized").asBoolean()).isTrue();
    assertThat(after.get("authorizationToken").asText()).isNotBlank();
    postJson("/api/v1/governance/decisions/" + id + "/execution", agentKey, "{\"status\": \"EXECUTED\"}")
        .andExpect(status().isOk());
  }

  @Test
  void aRejectedRefundNeverBecomesAuthorized() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    JsonNode decision = authorize(agentKey, refund(system, "CUST-102", 90000));
    String reject = "/api/v1/governance/approvals/" + decision.get("approval").get("id").asText() + "/reject";
    postJson(reject, compliance(), "{}").andExpect(status().isBadRequest());
    postJson(reject, compliance(), "{\"rationale\": \"Duplicate claim\"}").andExpect(status().isOk());

    JsonNode after = body(getAs("/api/v1/governance/decisions/" + decision.get("id").asText(), agentKey));
    assertThat(after.get("authorized").asBoolean()).isFalse();
    assertThat(after.get("approval").get("status").asText()).isEqualTo("REJECTED");
    assertThat(after.get("authorizationToken").isNull()).isTrue();
    postJson("/api/v1/governance/decisions/" + decision.get("id").asText() + "/execution", agentKey,
        "{\"status\": \"EXECUTED\"}").andExpect(status().isConflict());
  }

  @Test
  void anotherCustomersRecordAndUnknownActionsAreDenied() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    String read = """
        {"system": "%s", "action": "customer.read", "resource": {"customer_id": "%s"},
         "context": {"authorized_customer_id": "CUST-102"}}""";
    assertThat(authorize(agentKey, read.formatted(system, "CUST-102")).get("status").asText()).isEqualTo("ALLOW");
    JsonNode denied = authorize(agentKey, read.formatted(system, "CUST-999"));
    assertThat(denied.get("status").asText()).isEqualTo("DENY");
    assertThat(denied.get("authorized").asBoolean()).isFalse();
    assertThat(denied.get("rule").asText()).isEqualTo("customer.read");
    postJson("/api/v1/governance/decisions/" + denied.get("id").asText() + "/execution", agentKey,
        "{\"status\": \"EXECUTED\"}").andExpect(status().isConflict());

    JsonNode export = authorize(agentKey, "{\"system\": \"%s\", \"action\": \"customer.export\"}".formatted(system));
    assertThat(export.get("reason").asText()).isEqualTo("External customer-data export is prohibited");
    JsonNode unknown = authorize(agentKey, "{\"system\": \"%s\", \"action\": \"email.send\"}".formatted(system));
    assertThat(unknown.get("status").asText()).isEqualTo("DENY");
    assertThat(unknown.get("rule").asText()).isEqualTo("defaultDecision");
    JsonNode wrongModel = authorize(agentKey,
        "{\"system\": \"%s\", \"action\": \"claim.search\", \"model\": \"gpt-5@1\"}".formatted(system));
    assertThat(wrongModel.get("status").asText()).isEqualTo("DENY");
    assertThat(wrongModel.get("rule").asText()).isEqualTo("models.approved");

    postJson("/api/v1/governance/authorize", agentKey, "{\"system\": \"%s\", \"action\": \"Bad Action\"}".formatted(system))
        .andExpect(status().isBadRequest());
    postJson("/api/v1/governance/authorize", agentKey, "{\"system\": \"nope\", \"action\": \"claim.search\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void aSystemWithoutAPolicyDeniesEverything() throws Exception {
    String name = unique("bare-agent");
    apply(admin(), systemManifest(name, "2025-04-14", TOOLS));
    JsonNode decision = authorize(engineer(), "{\"system\": \"%s\", \"action\": \"claim.search\"}".formatted(name));
    assertThat(decision.get("status").asText()).isEqualTo("DENY");
    assertThat(decision.get("policy").isNull()).isTrue();
  }

  @Test
  void aDryRunDecidesWithoutRecordingAndMonitorModeRecordsWithoutBlocking() throws Exception {
    String system = claimsAgent();
    JsonNode dry = authorize(engineer(), refund(system, "CUST-102", 75000).replace("{\"system\"", "{\"dryRun\": true, \"system\""));
    assertThat(dry.get("status").asText()).isEqualTo("REVIEW");
    assertThat(dry.get("id").isNull()).isTrue();
    assertThat(body(getAs("/api/v1/governance/decisions?system=" + system, admin()))).isEmpty();

    String monitored = unique("monitored-agent");
    apply(admin(), systemManifest(monitored, "2025-04-14", TOOLS),
        policyManifest(unique("monitor-policy"), monitored, "{\"runtime\": \"monitor\"}", ""));
    JsonNode decision = authorize(engineer(), "{\"system\": \"%s\", \"action\": \"customer.export\"}".formatted(monitored));
    assertThat(decision.get("status").asText()).isEqualTo("DENY");
    assertThat(decision.get("enforced").asBoolean()).isFalse();
    assertThat(decision.get("authorized").asBoolean()).isTrue();
  }

  @Test
  void aPersonCannotApproveARequestTheyMadeThemselves() throws Exception {
    String name = unique("self-agent");
    String policy = policyManifest(unique("self-policy"), name, ENFORCE, "")
        .replace("\"approverRoles\": [\"COMPLIANCE_OFFICER\"]", "\"approverRoles\": [\"ADMIN\"]");
    apply(admin(), systemManifest(name, "2025-04-14", TOOLS), policy);
    JsonNode own = authorize(admin(), refund(name, "CUST-102", 75000));
    assertThat(own.get("agent").asText()).isEqualTo("user:" + ADMIN);
    postJson("/api/v1/governance/approvals/" + own.get("approval").get("id").asText() + "/approve", admin(), "{}")
        .andExpect(status().isForbidden());
  }

  @Test
  void nobodyAnswersARequestMadeThroughAnApiKeyTheyCreated() throws Exception {
    String name = unique("owner-agent");
    String policy = policyManifest(unique("owner-policy"), name, ENFORCE, "")
        .replace("\"approverRoles\": [\"COMPLIANCE_OFFICER\"]",
            "\"approverRoles\": [\"AI_ENGINEERING_LEAD\", \"COMPLIANCE_OFFICER\"]");
    apply(admin(), systemManifest(name, "2025-04-14", TOOLS), policy);
    // apiKey(...) keys belong to the engineer, so the engineer is accountable for what the agent asks.
    JsonNode decision = authorize(apiKey("owner-agent", ApiKeyScope.RUNTIME), refund(name, "CUST-102", 75000));
    String approve = "/api/v1/governance/approvals/" + decision.get("approval").get("id").asText() + "/approve";
    postJson(approve, engineer(), "{}").andExpect(status().isForbidden());
    postJson(approve, compliance(), "{}").andExpect(status().isOk());
  }

  @Test
  void onlyTheRequesterReceivesTheAuthorizationToken() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    String id = authorize(agentKey, refund(system, "CUST-102", 100)).get("id").asText();
    String path = "/api/v1/governance/decisions/" + id;

    assertThat(body(getAs(path, agentKey)).get("authorizationToken").asText()).isNotBlank();
    for (String other : List.of(apiKey("other-agent", ApiKeyScope.RUNTIME), apiKey("full", ApiKeyScope.FULL),
        engineer(), auditor())) {
      JsonNode seen = body(getAs(path, other).andExpect(status().isOk()));
      assertThat(seen.get("authorized").asBoolean()).isTrue();
      assertThat(seen.get("authorizationToken").isNull()).as("token minted for another caller").isTrue();
    }
    // A person who asked in their own session gets the token for their own request.
    String own = authorize(engineer(), refund(system, "CUST-102", 100)).get("id").asText();
    assertThat(body(getAs("/api/v1/governance/decisions/" + own, engineer())).get("authorizationToken").asText())
        .isNotBlank();
    assertThat(body(getAs("/api/v1/governance/decisions/" + own, admin())).get("authorizationToken").isNull()).isTrue();
  }

  // ---- key scopes and workspaces ------------------------------------------------------------------------

  @Test
  void runtimeAndReleaseGateKeysAreKeptToTheirOwnCalls() throws Exception {
    String system = claimsAgent();
    String runtime = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    String ci = apiKey("github-actions", ApiKeyScope.RELEASE_GATE);

    String id = authorize(runtime, refund(system, "CUST-102", 10)).get("id").asText();
    getAs("/api/v1/governance/decisions/" + id, runtime).andExpect(status().isOk());
    postJson("/api/v1/governance/observations", runtime,
        "{\"system\": \"%s\", \"model\": \"%s\"}".formatted(system, MODEL)).andExpect(status().isOk());
    for (String path : List.of("/api/v1/governance/decisions", "/api/v1/governance/approvals",
        "/api/v1/governance/status", "/api/v1/governance/evidence-bundle?system=" + system,
        "/api/v1/governance/release-check?system=" + system, "/api/v1/systems")) {
      getAs(path, runtime).andExpect(status().isForbidden());
    }

    getAs("/api/v1/governance/release-check?system=" + system, ci).andExpect(status().isOk());
    postJson("/api/v1/governance/validate", ci,
        "{\"resources\": [" + systemManifest(system, "2025-04-14", TOOLS) + "]}").andExpect(status().isOk());
    getAs("/api/v1/governance/validate?system=" + system, ci).andExpect(status().isOk());
    postJson("/api/v1/governance/simulate", ci,
        "{\"cases\": [{\"system\": \"%s\", \"action\": \"claim.search\"}]}".formatted(system)).andExpect(status().isOk());
    postJson("/api/v1/governance/authorize", ci, refund(system, "CUST-102", 10)).andExpect(status().isForbidden());
    getAs("/api/v1/governance/decisions/" + id, ci).andExpect(status().isForbidden());
  }

  @Test
  void anotherWorkspaceSeesNothing() throws Exception {
    String system = claimsAgent();
    JsonNode decision = authorize(engineer(), refund(system, "CUST-102", 75000));
    String id = decision.get("id").asText();
    String approvalId = decision.get("approval").get("id").asText();

    getAs("/api/v1/governance/decisions/" + id, otherAdmin()).andExpect(status().isNotFound());
    postJson("/api/v1/governance/decisions/" + id + "/execution", otherAdmin(), "{\"status\": \"EXECUTED\"}")
        .andExpect(status().isNotFound());
    postJson("/api/v1/governance/approvals/" + approvalId + "/approve", otherAdmin(), "{}")
        .andExpect(status().isNotFound());
    postJson("/api/v1/governance/authorize", otherAdmin(), refund(system, "CUST-102", 10))
        .andExpect(status().isNotFound());
    getAs("/api/v1/governance/resources?system=" + system, otherAdmin()).andExpect(status().isNotFound());
    getAs("/api/v1/governance/evidence-bundle?system=" + system, otherAdmin()).andExpect(status().isNotFound());
    assertThat(body(getAs("/api/v1/governance/decisions", otherAdmin()))).isEmpty();
    assertThat(body(getAs("/api/v1/governance/approvals", otherAdmin()))).isEmpty();
    assertThat(body(getAs("/api/v1/governance/status", otherAdmin()))).isEmpty();
    assertThat(body(getAs("/api/v1/governance/drift", otherAdmin()))).isEmpty();
  }

  @Test
  void auditorsReadButDoNotAct() throws Exception {
    String system = claimsAgent();
    authorize(engineer(), refund(system, "CUST-102", 10));
    assertThat(body(getAs("/api/v1/governance/decisions?system=" + system, auditor()))).hasSize(1);
    getAs("/api/v1/governance/closure?system=" + system, auditor()).andExpect(status().isOk());
    postJson("/api/v1/governance/authorize", auditor(), refund(system, "CUST-102", 10))
        .andExpect(status().isForbidden());
    postJson("/api/v1/governance/observations", auditor(), "{\"system\": \"%s\", \"model\": \"x\"}".formatted(system))
        .andExpect(status().isForbidden());
  }

  // ---- check before release -----------------------------------------------------------------------------

  @Test
  void validateCatchesANewToolBeforeItIsAppliedAndTheReleaseCheckBlocksIt() throws Exception {
    String system = claimsAgent();
    String withExport = "[\"claim_search\", \"customer_database\", \"process_refund\", \"export_all_customers\"]";

    // The pull request adds a tool. Nothing is applied yet; the dry run already fails.
    JsonNode dryRun = body(postJson("/api/v1/governance/validate", apiKey("ci", ApiKeyScope.RELEASE_GATE),
        "{\"resources\": [" + systemManifest(system, "2025-04-14", withExport) + "]}").andExpect(status().isOk()));
    assertThat(dryRun.get("valid").asBoolean()).isFalse();
    assertThat(dryRun.get("findings").get(0).get("code").asText()).isEqualTo("TOOL_UNGOVERNED");
    assertThat(dryRun.get("findings").get(0).get("subject").asText()).isEqualTo("export_all_customers");
    assertThat(body(getAs("/api/v1/governance/validate?system=" + system, admin())).get("valid").asBoolean()).isTrue();

    // Mapping the tool to the denied export action does not help.
    JsonNode mapped = body(postJson("/api/v1/governance/validate", admin(),
        "{\"resources\": [" + systemManifest(system, "2025-04-14", withExport) + ","
            + policyManifest("p", system, ENFORCE, ", \"export_all_customers\": \"customer.export\"") + "]}"));
    assertThat(mapped.get("findings").get(0).get("code").asText()).isEqualTo("TOOL_PROHIBITED");

    // A broken manifest is reported as a finding, not as a server error.
    JsonNode broken = body(postJson("/api/v1/governance/validate", admin(),
        "{\"resources\": [{\"apiVersion\": \"devernance.io/v1\", \"kind\": \"Nope\"}]}").andExpect(status().isOk()));
    assertThat(broken.get("valid").asBoolean()).isFalse();
    assertThat(broken.get("findings").get(0).get("code").asText()).isEqualTo("MANIFEST_INVALID");

    JsonNode before = body(getAs("/api/v1/governance/release-check?system=" + system, admin()));
    assertThat(before.get("findings").findValuesAsText("severity")).doesNotContain("ERROR");
    assertThat(before.get("decision").asText()).isEqualTo(before.get("releaseGate").get("decision").asText());

    // Someone applies it anyway: the release check is now BLOCKED with exit code 1.
    apply(admin(), systemManifest(system, "2025-04-14", withExport));
    JsonNode after = body(getAs("/api/v1/governance/release-check?system=" + system, admin()));
    assertThat(after.get("decision").asText()).isEqualTo("BLOCKED");
    assertThat(after.get("exitCode").asInt()).isEqualTo(1);
    assertThat(after.get("summary").asText()).contains("1 governance validation error");
  }

  @Test
  void simulateRunsPolicyTestsAgainstManifestsThatAreNotAppliedYet() throws Exception {
    String name = unique("draft-agent");
    String request = """
        {"resources": [%s, %s],
         "cases": [
           {"name": "small refund", "system": "%s", "action": "payment.refund",
            "context": {"amount": 20000}, "expect": "ALLOW"},
           {"name": "large refund", "system": "%s", "action": "payment.refund",
            "context": {"amount": 75000}, "expect": "REVIEW"},
           {"name": "export", "system": "%s", "action": "customer.export", "expect": "ALLOW"},
           {"name": "no expectation", "system": "%s", "action": "claim.search"}]}"""
        .formatted(systemManifest(name, "2025-04-14", TOOLS), policyManifest(unique("draft"), name, ENFORCE, ""),
            name, name, name, name);
    JsonNode result = body(postJson("/api/v1/governance/simulate", admin(), request).andExpect(status().isOk()));
    assertThat(result.get("passed").asBoolean()).isFalse();
    assertThat(result.get("results").get(0).get("passed").asBoolean()).isTrue();
    assertThat(result.get("results").get(1).get("passed").asBoolean()).isTrue();
    assertThat(result.get("results").get(2).get("passed").asBoolean()).isFalse();
    assertThat(result.get("results").get(2).get("status").asText()).isEqualTo("DENY");
    assertThat(result.get("results").get(3).get("passed").isNull()).isTrue();
    // Nothing was applied or recorded.
    getAs("/api/v1/governance/resources?system=" + name, admin()).andExpect(status().isNotFound());
  }

  // ---- reconcile ----------------------------------------------------------------------------------------

  @Test
  void driftIsDetectedBlocksTheReleaseAndClearsWhenTheChangeIsApproved() throws Exception {
    String system = claimsAgent();
    String runtime = apiKey("deploy-agent", ApiKeyScope.RUNTIME);
    String observe = """
        {"system": "%s", "environment": "production", "model": "%s", "promptHash": "sha256:prompt-1",
         "tools": ["process_refund", "claim_search", "customer_database"], "policyVersion": 1,
         "source": "kubernetes"}""";
    JsonNode inSync = body(postJson("/api/v1/governance/observations", runtime, observe.formatted(system, MODEL))
        .andExpect(status().isOk()));
    assertThat(inSync.get("inSync").asBoolean()).as(inSync.toString()).isTrue();

    // Two weeks later someone deploys another model and adds a tool, outside the governed process.
    JsonNode drifted = body(postJson("/api/v1/governance/observations", runtime,
        observe.formatted(system, "gpt-4.1@2026-01-15").replace("\"customer_database\"", "\"customer_database\", \"export_all_customers\"")));
    assertThat(drifted.get("inSync").asBoolean()).isFalse();
    assertThat(drifted.get("differences").findValuesAsText("property")).containsExactly("model", "tools");
    assertThat(drifted.get("reassessment").get("required").asBoolean()).isTrue();
    assertThat(drifted.get("reassessment").get("modelApproved").asBoolean()).isFalse();

    JsonNode check = body(getAs("/api/v1/governance/release-check?system=" + system, admin()));
    assertThat(check.get("decision").asText()).isEqualTo("BLOCKED");
    assertThat(check.get("drift").get(0).get("environment").asText()).isEqualTo("production");
    // The default is to keep serving while the change is reassessed.
    assertThat(authorize(runtime, refund(system, "CUST-102", 10)).get("status").asText()).isEqualTo("ALLOW");

    // Only the model change is approved; the extra tool is still drift.
    apply(admin(), systemManifest(system, "2026-01-15", TOOLS));
    JsonNode partly = body(getAs("/api/v1/governance/drift?system=" + system, admin()));
    assertThat(partly.get(0).get("differences").findValuesAsText("property")).containsExactly("tools");

    // The deployment is fixed and reports again: back in sync.
    JsonNode fixed = body(postJson("/api/v1/governance/observations", runtime,
        observe.formatted(system, "gpt-4.1@2026-01-15")));
    assertThat(fixed.get("inSync").asBoolean()).isTrue();

    JsonNode events = body(getAs("/api/v1/audit-events?systemId=" + check.get("systemId").asText() + "&limit=200", admin()));
    assertThat(events.findValuesAsText("eventType")).contains("governance.drift_detected", "governance.drift_resolved");
  }

  @Test
  void aPolicyCanStopASystemThatHasDrifted() throws Exception {
    String name = unique("strict-agent");
    apply(admin(), systemManifest(name, "2025-04-14", TOOLS),
        policyManifest(unique("strict"), name, "{\"onDrift\": \"block\"}", ""));
    assertThat(authorize(engineer(), "{\"system\": \"%s\", \"action\": \"claim.search\"}".formatted(name))
        .get("status").asText()).isEqualTo("ALLOW");
    postJson("/api/v1/governance/observations", engineer(),
        "{\"system\": \"%s\", \"model\": \"gpt-4.1@2099-01-01\"}".formatted(name)).andExpect(status().isOk());
    JsonNode blocked = authorize(engineer(), "{\"system\": \"%s\", \"action\": \"claim.search\"}".formatted(name));
    assertThat(blocked.get("status").asText()).isEqualTo("DENY");
    assertThat(blocked.get("rule").asText()).isEqualTo("enforcement.onDrift");

    // The environment in the request comes from the agent. Naming another one, even one that reports in sync,
    // does not step around drift reported in production.
    postJson("/api/v1/governance/observations", engineer(),
        "{\"system\": \"%s\", \"environment\": \"staging\", \"model\": \"%s\"}".formatted(name, MODEL))
        .andExpect(status().isOk());
    JsonNode staging = authorize(engineer(),
        "{\"system\": \"%s\", \"action\": \"claim.search\", \"environment\": \"staging\"}".formatted(name));
    assertThat(staging.get("status").asText()).isEqualTo("DENY");
    assertThat(staging.get("reason").asText()).contains("production");

    // Fixed in production: everything works again.
    postJson("/api/v1/governance/observations", engineer(),
        "{\"system\": \"%s\", \"model\": \"%s\"}".formatted(name, MODEL)).andExpect(status().isOk());
    assertThat(authorize(engineer(),
        "{\"system\": \"%s\", \"action\": \"claim.search\", \"environment\": \"staging\"}".formatted(name))
        .get("status").asText()).isEqualTo("ALLOW");
  }

  // ---- verify -------------------------------------------------------------------------------------------

  @Test
  void theEvidenceBundleVerifiesFromItsOwnBytesAndThePublishedKeys() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    String allowed = authorize(agentKey, refund(system, "CUST-102", 20000)).get("id").asText();
    postJson("/api/v1/governance/decisions/" + allowed + "/execution", agentKey, "{\"status\": \"EXECUTED\"}");
    JsonNode review = authorize(agentKey, refund(system, "CUST-102", 75000));
    postJson("/api/v1/governance/approvals/" + review.get("approval").get("id").asText() + "/approve", compliance(), "{}");
    authorize(agentKey, "{\"system\": \"%s\", \"action\": \"customer.export\"}".formatted(system));

    JsonNode bundle = body(getAs("/api/v1/governance/evidence-bundle?system=" + system, auditor())
        .andExpect(status().isOk()));
    String manifestText = bundle.get("manifest").asText();
    assertThat(sha256(manifestText)).isEqualTo(bundle.get("manifestSha256").asText());
    JsonNode manifest = json.readTree(manifestText);
    assertThat(manifest.get("counts").get("decisions").asInt()).isEqualTo(3);
    assertThat(manifest.get("counts").get("approvals").asInt()).isEqualTo(1);
    assertThat(manifest.get("counts").get("executions").asInt()).isEqualTo(1);
    assertThat(manifest.get("limitation").asText()).contains("does not prove that every action");

    List<String> paths = new ArrayList<>();
    for (JsonNode file : manifest.get("files")) {
      String path = file.get("path").asText();
      paths.add(path);
      assertThat(sha256(bundle.get("files").get(path).asText())).as(path).isEqualTo(file.get("sha256").asText());
    }
    assertThat(paths).hasSize(bundle.get("files").size());
    assertThat(paths).contains("decisions/" + allowed + ".json", "executions/" + allowed + ".json",
        "approvals/" + review.get("id").asText() + ".json", "drift/observations.json");
    assertThat(paths.stream().filter(p -> p.startsWith("policies/"))).hasSize(1);
    assertThat(paths.stream().filter(p -> p.startsWith("systems/"))).hasSize(1);
    JsonNode record = json.readTree(bundle.get("files").get("decisions/" + allowed + ".json").asText());
    assertThat(record.get("policy").get("contentSha256").asText()).hasSize(64);
    assertThat(record.get("agent").asText()).isEqualTo("claims-agent");

    String jwks = mockMvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
    JWSObject jws = JWSObject.parse(bundle.get("signature").asText());
    RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeyByKeyId(jws.getHeader().getKeyID());
    assertThat(jws.verify(new RSASSAVerifier(key))).isTrue();
    assertThat(jws.getPayload().toJSONObject().get("manifestSha256")).isEqualTo(bundle.get("manifestSha256").asText());

    // A period before anything happened is an empty, still verifiable bundle.
    JsonNode empty = body(getAs("/api/v1/governance/evidence-bundle?system=" + system
        + "&from=2020-01-01T00:00:00Z&to=2020-02-01T00:00:00Z", auditor()));
    assertThat(json.readTree(empty.get("manifest").asText()).get("counts").get("decisions").asInt()).isZero();
    getAs("/api/v1/governance/evidence-bundle?system=" + system + "&from=2021-01-01T00:00:00Z&to=2020-01-01T00:00:00Z",
        auditor()).andExpect(status().isBadRequest());
  }

  @Test
  void closureSaysWhereEvidenceIsMissingInsteadOfAssumingItExists() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    String executed = authorize(agentKey, refund(system, "CUST-102", 100)).get("id").asText();
    postJson("/api/v1/governance/decisions/" + executed + "/execution", agentKey, "{\"status\": \"EXECUTED\"}");
    authorize(agentKey, "{\"system\": \"%s\", \"action\": \"customer.export\"}".formatted(system));
    authorize(agentKey, "{\"system\": \"%s\", \"action\": \"claim.search\"}".formatted(system));

    JsonNode report = body(getAs("/api/v1/governance/closure?system=" + system, auditor()).andExpect(status().isOk()));
    assertThat(report.get("auditChainValid").asBoolean()).isTrue();
    assertThat(report.get("status").asText()).isEqualTo("OPEN");
    JsonNode refundRule = null;
    JsonNode searchRule = null;
    JsonNode readRule = null;
    JsonNode exportRule = null;
    for (JsonNode rule : report.get("rules")) {
      switch (rule.get("rule").asText()) {
        case "payment.refund" -> refundRule = rule;
        case "claim.search" -> searchRule = rule;
        case "customer.read" -> readRule = rule;
        case "customer.export" -> exportRule = rule;
        default -> { }
      }
    }
    // Asked, allowed, carried out and reported: closed.
    assertThat(refundRule.get("status").asText()).isEqualTo("CLOSED");
    assertThat(refundRule.get("requirement").asText()).isEqualTo("Refunds above 50,000 need human approval");
    assertThat(refundRule.get("executed").asLong()).isEqualTo(1);
    // Denied: nothing should run, so nothing is missing.
    assertThat(exportRule.get("status").asText()).isEqualTo("CLOSED");
    assertThat(exportRule.get("denied").asLong()).isEqualTo(1);
    // Allowed, but nobody reported whether the search ran.
    assertThat(searchRule.get("status").asText()).isEqualTo("OPEN");
    assertThat(searchRule.get("unreported").asLong()).isEqualTo(1);
    // Never exercised in the period: enforcement is not demonstrated.
    assertThat(readRule.get("status").asText()).isEqualTo("OPEN");
    assertThat(readRule.get("gaps").get(0).asText()).contains("not demonstrated");
  }

  @Test
  void decisionsCanBeQueriedForAnAuditAndEveryStepIsOnTheAuditChain() throws Exception {
    String system = claimsAgent();
    String agentKey = apiKey("claims-agent", ApiKeyScope.RUNTIME);
    authorize(agentKey, refund(system, "CUST-102", 20000));
    JsonNode review = authorize(agentKey, refund(system, "CUST-102", 75000));
    postJson("/api/v1/governance/approvals/" + review.get("approval").get("id").asText() + "/approve", compliance(), "{}");
    postJson("/api/v1/governance/decisions/" + review.get("id").asText() + "/execution", agentKey,
        "{\"status\": \"EXECUTED\", \"reference\": \"payment-1002\"}");
    JsonNode denied = authorize(agentKey, "{\"system\": \"%s\", \"action\": \"customer.export\"}".formatted(system));
    postJson("/api/v1/governance/decisions/" + denied.get("id").asText() + "/execution", agentKey,
        "{\"status\": \"EXECUTED\"}").andExpect(status().isConflict());

    // "Show me every refund that needed approval, and who approved it."
    JsonNode reviews = body(getAs("/api/v1/governance/decisions?system=" + system
        + "&action=payment.refund&status=REVIEW", auditor()).andExpect(status().isOk()));
    assertThat(reviews).hasSize(1);
    assertThat(reviews.get(0).get("approval").get("decidedBy").asText()).isEqualTo(COMPLIANCE.toString());
    assertThat(reviews.get(0).get("execution").get("reference").asText()).isEqualTo("payment-1002");
    // Lists are for reading: they never carry a usable token.
    assertThat(reviews.get(0).get("authorizationToken").isNull()).isTrue();
    assertThat(body(getAs("/api/v1/governance/decisions?system=" + system, auditor()))).hasSize(3);
    assertThat(body(getAs("/api/v1/governance/decisions?system=" + system + "&to=2020-01-01T00:00:00Z", auditor())))
        .isEmpty();
    assertThat(body(getAs("/api/v1/governance/decisions?system=" + system + "&limit=1", auditor()))).hasSize(1);

    JsonNode events = body(getAs("/api/v1/audit-events?systemId=" + review.get("systemId").asText() + "&limit=200", admin()));
    assertThat(events.findValuesAsText("eventType")).contains(
        "governance.resource_applied", "governance.decision", "governance.approval_approved",
        "governance.execution_recorded", "governance.execution_refused");
    assertThat(body(getAs("/api/v1/audit-events/verify-chain", admin())).get("valid").asBoolean()).isTrue();
  }

  @Test
  void statusAndGraphDescribeWhatIsGoverned() throws Exception {
    String system = claimsAgent();
    authorize(engineer(), refund(system, "CUST-102", 75000));
    JsonNode status = body(getAs("/api/v1/governance/status?system=" + system, auditor()).andExpect(status().isOk()));
    assertThat(status).hasSize(1);
    assertThat(status.get(0).get("model").asText()).isEqualTo(MODEL);
    assertThat(status.get(0).get("runtimeEnforcement").asText()).isEqualTo("enforce");
    assertThat(status.get(0).get("pendingApprovals").asLong()).isEqualTo(1);
    assertThat(status.get(0).get("decisionsLast24h").get("REVIEW").asLong()).isEqualTo(1);
    assertThat(status.get(0).get("validation").get("valid").asBoolean()).isTrue();

    JsonNode graph = body(getAs("/api/v1/governance/graph?system=" + system, auditor()).andExpect(status().isOk()));
    assertThat(graph.get("nodes").findValuesAsText("type"))
        .contains("system", "model", "policy", "tool", "action", "rule");
    assertThat(graph.get("edges").findValuesAsText("relation"))
        .contains("uses", "governs", "has", "performs", "decided by", "defines");
  }
}
