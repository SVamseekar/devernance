package os.assurance.eu.api.governance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides one request against one policy. Pure: same inputs, same answer, no clock and no database, so the
 * exact decision can be reproduced later from the stored request and policy version.
 *
 * <p>Order of evaluation (the first step that applies gives the answer):
 * <ol>
 *   <li>The model the caller reports is not on the approved list: DENY.</li>
 *   <li>No rule covers the action: the policy's default decision (deny unless set to allow).</li>
 *   <li>The rule says deny: DENY.</li>
 *   <li>A {@code require} condition is false or cannot be evaluated: DENY.</li>
 *   <li>A {@code review} entry cannot be evaluated: DENY. Its conditions all hold: REVIEW.</li>
 *   <li>Otherwise: ALLOW.</li>
 * </ol>
 * Anything that cannot be evaluated (a missing field, text where a number is needed) denies. A request is never
 * allowed because information was left out.
 */
public final class PolicyEvaluator {
  /** Name recorded as the rule when the model allowlist decided. */
  public static final String MODEL_RULE = "models.approved";
  /** Name recorded as the rule when no rule covered the action. */
  public static final String DEFAULT_RULE = "defaultDecision";

  private PolicyEvaluator() {
  }

  /** The outcome. {@code approverRoles} and {@code expiresAfterMinutes} are only meaningful for REVIEW. */
  public record Evaluation(
      DecisionStatus status, String rule, String reason, List<String> approverRoles, int expiresAfterMinutes) {
    static Evaluation of(DecisionStatus status, String rule, String reason) {
      return new Evaluation(status, rule, reason, List.of(), 0);
    }
  }

  /** What a request offers to conditions: the thing acted on and the trusted facts about the request. */
  public record Request(String action, Map<String, Object> resource, Map<String, Object> context, String model) {
  }

  public static Evaluation evaluate(PolicySpec policy, Request request) {
    if (request.model() != null && !policy.approvedModels().isEmpty() && !policy.approvesModel(request.model())) {
      return Evaluation.of(DecisionStatus.DENY, MODEL_RULE,
          "Model " + request.model() + " is not on the approved list");
    }
    Optional<PolicySpec.Rule> match = policy.ruleFor(request.action());
    if (match.isEmpty()) {
      return policy.defaultDecision() == DecisionStatus.ALLOW
          ? Evaluation.of(DecisionStatus.ALLOW, DEFAULT_RULE,
              "No rule covers " + request.action() + "; the policy default is allow")
          : Evaluation.of(DecisionStatus.DENY, DEFAULT_RULE,
              "No rule covers " + request.action() + "; the policy default is deny");
    }
    PolicySpec.Rule rule = match.get();
    if (rule.decision() == DecisionStatus.DENY) {
      return Evaluation.of(DecisionStatus.DENY, rule.action(),
          rule.reason() != null ? rule.reason() : "The policy denies " + rule.action());
    }
    for (PolicySpec.Condition condition : rule.require()) {
      Truth truth = test(condition, request);
      if (truth != Truth.TRUE) {
        return Evaluation.of(DecisionStatus.DENY, rule.action(),
            (truth == Truth.UNKNOWN ? "Requirement could not be evaluated: " : "Requirement not met: ")
                + condition.describe());
      }
    }
    for (PolicySpec.Review review : rule.review()) {
      boolean applies = true;
      for (PolicySpec.Condition condition : review.when()) {
        Truth truth = test(condition, request);
        if (truth == Truth.UNKNOWN) {
          return Evaluation.of(DecisionStatus.DENY, rule.action(),
              "Review condition could not be evaluated: " + condition.describe());
        }
        if (truth == Truth.FALSE) {
          applies = false;
          break;
        }
      }
      if (applies) {
        return new Evaluation(DecisionStatus.REVIEW, rule.action(), review.reason(), review.approverRoles(),
            review.expiresAfterMinutes());
      }
    }
    return Evaluation.of(DecisionStatus.ALLOW, rule.action(),
        rule.reason() != null ? rule.reason() : "The policy allows " + rule.action());
  }

  enum Truth {
    TRUE,
    FALSE,
    /** A field is missing or has the wrong type, so the condition says nothing either way. */
    UNKNOWN
  }

  static Truth test(PolicySpec.Condition condition, Request request) {
    Object left = resolve(condition.field(), request);
    if (condition.op().equals("exists")) {
      return left != null ? Truth.TRUE : Truth.FALSE;
    }
    if (left == null) {
      return Truth.UNKNOWN;
    }
    if (condition.op().equals("in")) {
      for (Object candidate : (List<?>) condition.value()) {
        if (same(left, candidate)) {
          return Truth.TRUE;
        }
      }
      return Truth.FALSE;
    }
    Object right = condition.valueFrom() != null ? resolve(condition.valueFrom(), request) : condition.value();
    if (right == null) {
      return Truth.UNKNOWN;
    }
    switch (condition.op()) {
      case "eq" -> {
        return same(left, right) ? Truth.TRUE : Truth.FALSE;
      }
      case "ne" -> {
        return same(left, right) ? Truth.FALSE : Truth.TRUE;
      }
      default -> {
        if (!(left instanceof Number a) || !(right instanceof Number b)) {
          return Truth.UNKNOWN;
        }
        int order = decimal(a).compareTo(decimal(b));
        boolean holds = switch (condition.op()) {
          case "gt" -> order > 0;
          case "gte" -> order >= 0;
          case "lt" -> order < 0;
          default -> order <= 0;
        };
        return holds ? Truth.TRUE : Truth.FALSE;
      }
    }
  }

  /** Numbers compare by value (50000 equals 50000.0); everything else must be the same type and equal. */
  private static boolean same(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) {
      return decimal(x).compareTo(decimal(y)) == 0;
    }
    if (a instanceof String || a instanceof Boolean) {
      return a.equals(b);
    }
    return false;
  }

  private static BigDecimal decimal(Number number) {
    return new BigDecimal(number.toString());
  }

  /** The scalar at a path such as {@code context.amount}, or null when it is absent or not a scalar. */
  private static Object resolve(String path, Request request) {
    String[] parts = path.split("\\.");
    Object current = parts[0].equals("resource") ? request.resource() : request.context();
    for (int i = 1; i < parts.length; i++) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(parts[i]);
    }
    return current instanceof String || current instanceof Number || current instanceof Boolean ? current : null;
  }
}
