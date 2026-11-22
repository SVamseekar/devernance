package os.assurance.eu.api.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GovernanceValidatorTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Map<String, Object> map(String json) {
    try {
      return JSON.readValue(json, new TypeReference<>() { });
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static SystemSpec system(String tools, String modelVersion) {
    List<String> errors = new ArrayList<>();
    SystemSpec spec = SystemSpec.parse(map("""
        {"owner": "Claims", "purpose": "Process claims", "riskClass": "limited", "riskBasis": "Art 50",
         "deploymentRegion": "EU", "model": {"provider": "openai", "name": "gpt-4.1", "version": "%s"},
         "tools": %s}""".formatted(modelVersion, tools)), errors);
    assertThat(errors).isEmpty();
    return spec;
  }

  private static PolicySpec policy() {
    List<String> errors = new ArrayList<>();
    PolicySpec spec = PolicySpec.parse(map("""
        {"target": {"system": "claims-agent"},
         "models": {"approved": ["gpt-4.1@2025-04-14"]},
         "tools": {"claim_search": "claim.search", "process_refund": "payment.refund",
                   "export_all_customers": "customer.export", "send_email": "email.send"},
         "rules": [
           {"action": "claim.search", "decision": "allow"},
           {"action": "payment.refund", "decision": "allow"},
           {"action": "customer.export", "decision": "deny", "reason": "External export is prohibited"}]}"""),
        errors);
    assertThat(errors).isEmpty();
    return spec;
  }

  private static List<String> codes(List<GovernanceFinding> findings) {
    return findings.stream().map(f -> f.code() + ":" + f.subject()).toList();
  }

  @Test
  void aSystemWhoseToolsAllHaveAnAllowedActionIsValid() {
    List<GovernanceFinding> findings = GovernanceValidator.validate(
        "claims-agent", system("[\"claim_search\", \"process_refund\"]", "2025-04-14"), policy());
    assertThat(findings.stream().filter(GovernanceFinding::isError)).isEmpty();
    // Mapped in the policy but not declared by the system: worth a look, not a failure.
    assertThat(codes(findings)).containsExactly(
        "TOOL_NOT_DECLARED:export_all_customers", "TOOL_NOT_DECLARED:send_email");
  }

  @Test
  void flagsAToolThePolicyDoesNotKnowAndAToolWhoseActionIsDenied() {
    List<GovernanceFinding> findings = GovernanceValidator.validate("claims-agent",
        system("[\"claim_search\", \"export_all_customers\", \"delete_claims\", \"send_email\"]", "2025-04-14"),
        policy());
    assertThat(codes(findings.stream().filter(GovernanceFinding::isError).toList())).containsExactly(
        "TOOL_PROHIBITED:export_all_customers",   // rule denies it
        "TOOL_UNGOVERNED:delete_claims",          // no mapping at all
        "TOOL_PROHIBITED:send_email");            // mapped, but no rule and the default is deny
    assertThat(findings.get(0).message()).contains("External export is prohibited");
  }

  @Test
  void flagsAModelThatIsNotOnTheApprovedListAndAMissingPolicy() {
    assertThat(codes(GovernanceValidator.validate("claims-agent", system("[]", "2026-01-01"), policy())))
        .contains("MODEL_NOT_APPROVED:gpt-4.1@2026-01-01");
    assertThat(codes(GovernanceValidator.validate("claims-agent", system("[]", "2025-04-14"), null)))
        .containsExactly("POLICY_MISSING:claims-agent");
  }

  @Test
  void aSystemSpecRejectsUnknownKeysBadRiskClassesAndDuplicateTools() {
    List<String> errors = new ArrayList<>();
    SystemSpec.parse(map("""
        {"owner": "Claims", "purpose": "p", "riskClass": "severe", "riskBasis": "b", "deploymentRegion": "EU",
         "modle": {}, "tools": ["a", "a", "bad tool"]}"""), errors);
    assertThat(errors).anyMatch(e -> e.contains("unknown key 'modle'"))
        .anyMatch(e -> e.contains("spec.riskClass"))
        .anyMatch(e -> e.contains("every tool must be listed once"))
        .anyMatch(e -> e.contains("'bad tool'"));
  }
}
