package os.assurance.eu.api.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyEvaluatorTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String CLAIMS_POLICY = """
      {
        "target": {"system": "claims-agent"},
        "models": {"approved": ["gpt-4.1@2025-04-14", "claude-any"]},
        "tools": {"process_refund": "payment.refund"},
        "rules": [
          {"action": "claim.search", "decision": "allow"},
          {"action": "customer.read", "decision": "allow",
           "require": [{"field": "resource.customer_id", "op": "eq", "valueFrom": "context.authorized_customer_id"}]},
          {"action": "payment.refund", "decision": "allow",
           "require": [{"field": "context.currency", "op": "in", "value": ["INR", "EUR"]}],
           "review": [{"when": [{"field": "context.amount", "op": "gt", "value": 50000}],
                       "approverRoles": ["compliance_officer"], "reason": "High-value refund",
                       "expiresAfterMinutes": 60}]},
          {"action": "customer.export", "decision": "deny", "reason": "External export is prohibited"},
          {"action": "payment.*", "decision": "deny"}
        ]
      }""";

  private static Map<String, Object> map(String json) {
    try {
      return JSON.readValue(json, new TypeReference<>() { });
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static PolicySpec policy(String json) {
    List<String> errors = new ArrayList<>();
    PolicySpec spec = PolicySpec.parse(map(json), errors);
    assertThat(errors).isEmpty();
    return spec;
  }

  private static List<String> errorsOf(String json) {
    List<String> errors = new ArrayList<>();
    PolicySpec.parse(map(json), errors);
    return errors;
  }

  private static PolicyEvaluator.Evaluation decide(String action, String resource, String context, String model) {
    return PolicyEvaluator.evaluate(policy(CLAIMS_POLICY),
        new PolicyEvaluator.Request(action, map(resource), map(context), model));
  }

  @Test
  void allowsARefundAtOrBelowTheThreshold() {
    assertThat(decide("payment.refund", "{}", "{\"amount\": 20000, \"currency\": \"INR\"}", null).status())
        .isEqualTo(DecisionStatus.ALLOW);
    assertThat(decide("payment.refund", "{}", "{\"amount\": 50000, \"currency\": \"INR\"}", null).status())
        .isEqualTo(DecisionStatus.ALLOW);
  }

  @Test
  void sendsARefundAboveTheThresholdToReviewWithThePolicyRolesAndExpiry() {
    PolicyEvaluator.Evaluation result =
        decide("payment.refund", "{}", "{\"amount\": 50000.01, \"currency\": \"INR\"}", null);
    assertThat(result.status()).isEqualTo(DecisionStatus.REVIEW);
    assertThat(result.rule()).isEqualTo("payment.refund");
    assertThat(result.reason()).isEqualTo("High-value refund");
    assertThat(result.approverRoles()).containsExactly("COMPLIANCE_OFFICER");
    assertThat(result.expiresAfterMinutes()).isEqualTo(60);
  }

  @Test
  void deniesWhenInformationNeededForADecisionIsMissingOrOfTheWrongType() {
    // No amount: the review condition cannot be evaluated, so the refund is not silently allowed.
    assertThat(decide("payment.refund", "{}", "{\"currency\": \"INR\"}", null).status())
        .isEqualTo(DecisionStatus.DENY);
    assertThat(decide("payment.refund", "{}", "{\"amount\": \"75000\", \"currency\": \"INR\"}", null).status())
        .isEqualTo(DecisionStatus.DENY);
    // No currency: the requirement cannot be evaluated.
    PolicyEvaluator.Evaluation noCurrency = decide("payment.refund", "{}", "{\"amount\": 10}", null);
    assertThat(noCurrency.status()).isEqualTo(DecisionStatus.DENY);
    assertThat(noCurrency.reason()).contains("could not be evaluated");
  }

  @Test
  void deniesAccessToAnotherCustomersRecord() {
    assertThat(decide("customer.read", "{\"customer_id\": \"CUST-102\"}",
        "{\"authorized_customer_id\": \"CUST-102\"}", null).status()).isEqualTo(DecisionStatus.ALLOW);
    PolicyEvaluator.Evaluation other = decide("customer.read", "{\"customer_id\": \"CUST-999\"}",
        "{\"authorized_customer_id\": \"CUST-102\"}", null);
    assertThat(other.status()).isEqualTo(DecisionStatus.DENY);
    assertThat(other.reason()).startsWith("Requirement not met");
    // Without the trusted context the comparison cannot be made.
    assertThat(decide("customer.read", "{\"customer_id\": \"CUST-102\"}", "{}", null).status())
        .isEqualTo(DecisionStatus.DENY);
  }

  @Test
  void theMostSpecificRuleWinsAndUncoveredActionsGetTheDefault() {
    // payment.refund (exact) beats payment.* (prefix); payment.payout only matches the prefix.
    assertThat(decide("payment.payout", "{}", "{}", null).rule()).isEqualTo("payment.*");
    assertThat(decide("payment.payout", "{}", "{}", null).status()).isEqualTo(DecisionStatus.DENY);
    PolicyEvaluator.Evaluation uncovered = decide("email.send", "{}", "{}", null);
    assertThat(uncovered.status()).isEqualTo(DecisionStatus.DENY);
    assertThat(uncovered.rule()).isEqualTo(PolicyEvaluator.DEFAULT_RULE);
    assertThat(decide("customer.export", "{}", "{}", null).reason()).isEqualTo("External export is prohibited");
  }

  @Test
  void aPrefixRuleDoesNotMatchAnActionThatOnlySharesItsLetters() {
    PolicySpec spec = policy("""
        {"target": {"system": "s"}, "rules": [{"action": "pay.*", "decision": "allow"}]}""");
    assertThat(spec.ruleFor("pay.refund")).isPresent();
    assertThat(spec.ruleFor("payment.refund")).isEmpty();
    assertThat(spec.ruleFor("pay")).isEmpty();
  }

  @Test
  void deniesAModelThatIsNotApprovedAndAcceptsAnyVersionOfAnUnversionedEntry() {
    assertThat(decide("claim.search", "{}", "{}", "gpt-4.1@2025-04-14").status()).isEqualTo(DecisionStatus.ALLOW);
    PolicyEvaluator.Evaluation other = decide("claim.search", "{}", "{}", "gpt-4.1@2026-01-01");
    assertThat(other.status()).isEqualTo(DecisionStatus.DENY);
    assertThat(other.rule()).isEqualTo(PolicyEvaluator.MODEL_RULE);
    assertThat(decide("claim.search", "{}", "{}", "claude-any@7").status()).isEqualTo(DecisionStatus.ALLOW);
    // The caller did not say which model runs: the allowlist cannot be checked here (drift detection covers it).
    assertThat(decide("claim.search", "{}", "{}", null).status()).isEqualTo(DecisionStatus.ALLOW);
  }

  @Test
  void numbersCompareByValueAndTextNeverEqualsANumber() {
    PolicySpec spec = policy("""
        {"target": {"system": "s"}, "rules": [{"action": "a.b", "decision": "allow",
          "require": [{"field": "context.n", "op": "eq", "value": 5}]}]}""");
    assertThat(PolicyEvaluator.evaluate(spec, new PolicyEvaluator.Request("a.b", Map.of(), map("{\"n\": 5.0}"), null))
        .status()).isEqualTo(DecisionStatus.ALLOW);
    assertThat(PolicyEvaluator.evaluate(spec, new PolicyEvaluator.Request("a.b", Map.of(), map("{\"n\": \"5\"}"), null))
        .status()).isEqualTo(DecisionStatus.DENY);
  }

  @Test
  void aReviewWithoutConditionsAlwaysAppliesAndGetsTheDefaultApprovers() {
    PolicySpec spec = policy("""
        {"target": {"system": "s"}, "rules": [{"action": "a.b", "decision": "allow", "review": [{}]}]}""");
    PolicyEvaluator.Evaluation result =
        PolicyEvaluator.evaluate(spec, new PolicyEvaluator.Request("a.b", Map.of(), Map.of(), null));
    assertThat(result.status()).isEqualTo(DecisionStatus.REVIEW);
    assertThat(result.approverRoles()).containsExactly("ADMIN", "COMPLIANCE_OFFICER");
    assertThat(result.expiresAfterMinutes()).isEqualTo(PolicySpec.DEFAULT_EXPIRY_MINUTES);
  }

  @Test
  void defaultsAreDenyBlockingEnforceAndContinueOnDrift() {
    PolicySpec spec = policy("{\"target\": {\"system\": \"s\"}}");
    assertThat(spec.defaultDecision()).isEqualTo(DecisionStatus.DENY);
    assertThat(spec.deploymentBlocking()).isTrue();
    assertThat(spec.runtimeEnforced()).isTrue();
    assertThat(spec.driftBlocks()).isFalse();
  }

  @Test
  void rejectsMisspelledKeysAndImpossibleRulesInsteadOfIgnoringThem() {
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "rules": [{"action": "a.b", "decison": "deny"}]}"""))
        .anyMatch(e -> e.contains("unknown key 'decison'"))
        .anyMatch(e -> e.contains("decision: is required"));
    assertThat(errorsOf("{\"rules\": []}")).anyMatch(e -> e.contains("spec.target.system: is required"));
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "rules": [
          {"action": "a.b", "decision": "allow"}, {"action": "a.b", "decision": "deny"}]}"""))
        .anyMatch(e -> e.contains("already has a rule"));
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "rules": [{"action": "a.b", "decision": "deny",
          "require": [{"field": "context.x", "op": "exists"}]}]}"""))
        .anyMatch(e -> e.contains("a deny rule cannot have require or review"));
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "rules": [{"action": "Pay Refund", "decision": "allow"}]}"""))
        .anyMatch(e -> e.contains("must look like payment.refund"));
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "rules": [{"action": "a.b", "decision": "allow", "require": [
          {"field": "amount", "op": "gt", "value": 1},
          {"field": "context.amount", "op": "gt", "value": "big"},
          {"field": "context.amount", "op": "between", "value": 1},
          {"field": "context.amount", "op": "eq"},
          {"field": "context.amount", "op": "eq", "value": 1, "valueFrom": "context.other"}]}]}"""))
        .hasSize(5);
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "rules": [{"action": "a.b", "decision": "allow",
          "review": [{"approverRoles": ["AUDITOR"], "expiresAfterMinutes": 0}]}]}"""))
        .anyMatch(e -> e.contains("approverRoles"))
        .anyMatch(e -> e.contains("expiresAfterMinutes"));
    assertThat(errorsOf("""
        {"target": {"system": "s"}, "tools": {"refund": "Not An Action"},
         "enforcement": {"runtime": "sometimes"}, "defaultDecision": "maybe"}"""))
        .hasSize(3);
  }
}
