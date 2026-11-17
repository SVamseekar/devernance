package os.assurance.eu.api.governance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A checked {@code GovernancePolicy} spec. See docs/GOVERNANCE_POLICY_REFERENCE.md for every field.
 *
 * <p>In short: {@code rules} say, per action, allow or deny; an allow rule may add {@code require} conditions
 * (all must hold, otherwise DENY) and {@code review} entries (when one matches, a person must approve). An
 * action no rule covers gets {@code defaultDecision}, which is deny unless the policy says otherwise.
 */
public record PolicySpec(
    String targetSystem,
    List<String> approvedModels,
    DecisionStatus defaultDecision,
    Map<String, String> tools,
    List<Rule> rules,
    boolean deploymentBlocking,
    boolean runtimeEnforced,
    boolean driftBlocks) {

  /** A rule's {@code action}: an exact action, a prefix such as {@code payment.*}, or {@code *}. */
  static final Pattern ACTION_PATTERN = Pattern.compile("^(\\*|[a-z0-9_]+(\\.[a-z0-9_]+)*(\\.\\*)?)$");
  static final Pattern ACTION = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)*$");
  static final Pattern FIELD = Pattern.compile("^(resource|context)(\\.[A-Za-z0-9_-]+)+$");
  static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "gte", "lt", "lte", "in", "exists");
  static final Set<String> APPROVER_ROLES =
      Set.of("ADMIN", "AI_ENGINEERING_LEAD", "COMPLIANCE_OFFICER", "LEGAL_COUNSEL");
  static final List<String> DEFAULT_APPROVER_ROLES = List.of("ADMIN", "COMPLIANCE_OFFICER");
  static final int DEFAULT_EXPIRY_MINUTES = 24 * 60;
  static final int MAX_EXPIRY_MINUTES = 30 * 24 * 60;
  static final int MAX_ACTION_LENGTH = 128;

  private static final Set<String> KEYS =
      Set.of("target", "models", "defaultDecision", "tools", "rules", "enforcement");
  private static final Set<String> RULE_KEYS =
      Set.of("action", "decision", "reason", "requirement", "require", "review");
  private static final Set<String> REVIEW_KEYS = Set.of("when", "approverRoles", "reason", "expiresAfterMinutes");
  private static final Set<String> CONDITION_KEYS = Set.of("field", "op", "value", "valueFrom");

  /** One comparison. {@code value} is a literal; {@code valueFrom} names another field to compare with. */
  public record Condition(String field, String op, Object value, String valueFrom) {
    /** How the condition reads in a decision reason, e.g. {@code context.amount gt 50000}. */
    public String describe() {
      if ("exists".equals(op)) {
        return field + " exists";
      }
      return field + " " + op + " " + (valueFrom != null ? valueFrom : String.valueOf(value));
    }
  }

  /** When {@code when} holds (always, if empty), the action needs approval by one of {@code approverRoles}. */
  public record Review(List<Condition> when, List<String> approverRoles, String reason, int expiresAfterMinutes) {
    public Review {
      when = List.copyOf(when);
      approverRoles = List.copyOf(approverRoles);
    }
  }

  public record Rule(
      String action,
      DecisionStatus decision,
      String reason,
      String requirement,
      List<Condition> require,
      List<Review> review) {
    public Rule {
      require = List.copyOf(require);
      review = List.copyOf(review);
    }

    boolean matches(String requested) {
      if (action.equals("*")) {
        return true;
      }
      if (action.endsWith(".*")) {
        return requested.startsWith(action.substring(0, action.length() - 1));
      }
      return action.equals(requested);
    }

    /** Exact rules beat prefixes, longer prefixes beat shorter ones, and {@code *} is last. */
    int specificity() {
      if (action.equals("*")) {
        return 0;
      }
      return action.endsWith(".*") ? action.length() : Integer.MAX_VALUE;
    }
  }

  public PolicySpec {
    approvedModels = List.copyOf(approvedModels);
    tools = Map.copyOf(tools);
    rules = List.copyOf(rules);
  }

  /** The most specific rule covering {@code action}, if any. */
  public Optional<Rule> ruleFor(String action) {
    Rule best = null;
    for (Rule rule : rules) {
      if (rule.matches(action) && (best == null || rule.specificity() > best.specificity())) {
        best = rule;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * True when {@code model} ({@code name} or {@code name@version}) is on the approved list. An entry without a
   * version approves every version of that model. An empty list approves nothing in particular: no model check.
   */
  public boolean approvesModel(String model) {
    if (approvedModels.contains(model)) {
      return true;
    }
    int at = model.lastIndexOf('@');
    return at > 0 && approvedModels.contains(model.substring(0, at));
  }

  /** Reads and checks a spec. Problems are added to {@code errors}; the result is only usable when none were. */
  public static PolicySpec parse(Map<String, Object> spec, List<String> errors) {
    SpecReader read = new SpecReader(errors);
    read.onlyKeys("spec", spec, KEYS);

    Map<String, Object> target = read.object("spec", spec, "target");
    read.onlyKeys("spec.target", target, Set.of("system"));
    String targetSystem = read.string("spec.target", target, "system", true, 128);

    Map<String, Object> models = read.object("spec", spec, "models");
    read.onlyKeys("spec.models", models, Set.of("approved"));
    List<String> approved = read.strings("spec.models", models, "approved", 100, 256);

    DecisionStatus defaultDecision = "allow".equals(
        read.choice("spec", spec, "defaultDecision", Set.of("allow", "deny"), "deny"))
        ? DecisionStatus.ALLOW : DecisionStatus.DENY;

    Map<String, String> tools = new LinkedHashMap<>();
    Map<String, Object> rawTools = read.object("spec", spec, "tools");
    if (rawTools.size() > 200) {
      read.error("spec.tools: must have at most 200 entries");
    }
    rawTools.forEach((tool, action) -> {
      if (!SystemSpec.TOOL_NAME.matcher(tool).matches()) {
        read.error("spec.tools: tool name '" + tool + "' may only contain letters, digits and _ . : -");
      } else if (!(action instanceof String text) || !isAction(text)) {
        read.error("spec.tools." + tool + ": must be an action such as payment.refund");
      } else {
        tools.put(tool, text);
      }
    });

    List<Rule> rules = new ArrayList<>();
    List<Object> rawRules = read.list("spec", spec, "rules");
    if (rawRules.size() > 200) {
      read.error("spec.rules: must have at most 200 entries");
      rawRules = List.of();
    }
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < rawRules.size(); i++) {
      Rule rule = parseRule("spec.rules[" + i + "]", rawRules.get(i), read);
      if (rule != null) {
        if (!seen.add(rule.action())) {
          read.error("spec.rules[" + i + "].action: '" + rule.action() + "' already has a rule");
        }
        rules.add(rule);
      }
    }

    Map<String, Object> enforcement = read.object("spec", spec, "enforcement");
    read.onlyKeys("spec.enforcement", enforcement, Set.of("deployment", "runtime", "onDrift"));
    boolean blocking = "blocking".equals(
        read.choice("spec.enforcement", enforcement, "deployment", Set.of("blocking", "advisory"), "blocking"));
    boolean enforced = "enforce".equals(
        read.choice("spec.enforcement", enforcement, "runtime", Set.of("enforce", "monitor"), "enforce"));
    boolean driftBlocks = "block".equals(
        read.choice("spec.enforcement", enforcement, "onDrift", Set.of("continue", "block"), "continue"));

    return new PolicySpec(targetSystem == null ? "" : targetSystem, approved, defaultDecision, tools, rules,
        blocking, enforced, driftBlocks);
  }

  static boolean isAction(String text) {
    return text.length() <= MAX_ACTION_LENGTH && ACTION.matcher(text).matches();
  }

  @SuppressWarnings("unchecked")
  private static Rule parseRule(String path, Object raw, SpecReader read) {
    if (!(raw instanceof Map<?, ?> rawMap) || !rawMap.keySet().stream().allMatch(String.class::isInstance)) {
      read.error(path + ": must be an object");
      return null;
    }
    Map<String, Object> map = (Map<String, Object>) rawMap;
    read.onlyKeys(path, map, RULE_KEYS);
    String action = read.string(path, map, "action", true, MAX_ACTION_LENGTH);
    if (action != null && !ACTION_PATTERN.matcher(action).matches()) {
      read.error(path + ".action: must look like payment.refund, payment.* or *");
      action = null;
    }
    Object rawDecision = map.get("decision");
    if (rawDecision == null) {
      read.error(path + ".decision: is required (allow or deny)");
    }
    boolean allow = "allow".equals(read.choice(path, map, "decision", Set.of("allow", "deny"), "deny"));
    String reason = read.string(path, map, "reason", false, 512);
    String requirement = read.string(path, map, "requirement", false, 512);

    List<Condition> require = parseConditions(path + ".require", read.list(path, map, "require"), read);
    List<Review> review = new ArrayList<>();
    List<Object> rawReview = read.list(path, map, "review");
    if (rawReview.size() > 20) {
      read.error(path + ".review: must have at most 20 entries");
      rawReview = List.of();
    }
    for (int i = 0; i < rawReview.size(); i++) {
      Review entry = parseReview(path + ".review[" + i + "]", rawReview.get(i), read);
      if (entry != null) {
        review.add(entry);
      }
    }
    if (!allow && (!require.isEmpty() || !review.isEmpty())) {
      read.error(path + ": a deny rule cannot have require or review (it always denies)");
    }
    if (action == null) {
      return null;
    }
    return new Rule(action, allow ? DecisionStatus.ALLOW : DecisionStatus.DENY, reason, requirement, require, review);
  }

  @SuppressWarnings("unchecked")
  private static Review parseReview(String path, Object raw, SpecReader read) {
    if (!(raw instanceof Map<?, ?> rawMap) || !rawMap.keySet().stream().allMatch(String.class::isInstance)) {
      read.error(path + ": must be an object");
      return null;
    }
    Map<String, Object> map = (Map<String, Object>) rawMap;
    read.onlyKeys(path, map, REVIEW_KEYS);
    List<Condition> when = parseConditions(path + ".when", read.list(path, map, "when"), read);
    List<String> roles = new ArrayList<>();
    for (String role : read.strings(path, map, "approverRoles", 10, 64)) {
      String upper = role.toUpperCase(java.util.Locale.ROOT);
      if (!APPROVER_ROLES.contains(upper)) {
        read.error(path + ".approverRoles: '" + role + "' must be one of "
            + String.join(", ", APPROVER_ROLES.stream().sorted().toList()));
      } else if (!roles.contains(upper)) {
        roles.add(upper);
      }
    }
    if (roles.isEmpty()) {
      roles.addAll(DEFAULT_APPROVER_ROLES);
    }
    String reason = read.string(path, map, "reason", false, 512);
    int expires = DEFAULT_EXPIRY_MINUTES;
    Object rawExpires = map.get("expiresAfterMinutes");
    if (rawExpires != null) {
      if (rawExpires instanceof Integer minutes && minutes >= 1 && minutes <= MAX_EXPIRY_MINUTES) {
        expires = minutes;
      } else {
        read.error(path + ".expiresAfterMinutes: must be a whole number from 1 to " + MAX_EXPIRY_MINUTES);
      }
    }
    return new Review(when, roles, reason == null ? "Human approval required" : reason, expires);
  }

  @SuppressWarnings("unchecked")
  private static List<Condition> parseConditions(String path, List<Object> raw, SpecReader read) {
    if (raw.size() > 20) {
      read.error(path + ": must have at most 20 conditions");
      return List.of();
    }
    List<Condition> out = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      String at = path + "[" + i + "]";
      if (!(raw.get(i) instanceof Map<?, ?> rawMap) || !rawMap.keySet().stream().allMatch(String.class::isInstance)) {
        read.error(at + ": must be an object with field and op");
        continue;
      }
      Map<String, Object> map = (Map<String, Object>) rawMap;
      read.onlyKeys(at, map, CONDITION_KEYS);
      String field = read.string(at, map, "field", true, 200);
      if (field != null && !FIELD.matcher(field).matches()) {
        read.error(at + ".field: must start with resource. or context., for example context.amount");
        field = null;
      }
      String op = read.string(at, map, "op", true, 16);
      if (op != null && !OPERATORS.contains(op)) {
        read.error(at + ".op: must be one of " + String.join(", ", OPERATORS.stream().sorted().toList()));
        op = null;
      }
      Object value = map.get("value");
      String valueFrom = read.string(at, map, "valueFrom", false, 200);
      if (valueFrom != null && !FIELD.matcher(valueFrom).matches()) {
        read.error(at + ".valueFrom: must start with resource. or context.");
        valueFrom = null;
      }
      if (field == null || op == null) {
        continue;
      }
      if (!operandsFit(at, op, value, map.containsKey("valueFrom"), read)) {
        continue;
      }
      out.add(new Condition(field, op, value, valueFrom));
    }
    return out;
  }

  private static boolean operandsFit(String at, String op, Object value, boolean hasValueFrom, SpecReader read) {
    boolean hasValue = value != null;
    switch (op) {
      case "exists" -> {
        if (hasValue || hasValueFrom) {
          read.error(at + ": op exists takes no value or valueFrom");
          return false;
        }
        return true;
      }
      case "in" -> {
        if (hasValueFrom || !(value instanceof List<?> items) || items.isEmpty() || items.size() > 100
            || !items.stream().allMatch(PolicySpec::isScalar)) {
          read.error(at + ": op in needs value to be a list of 1 to 100 strings, numbers or booleans");
          return false;
        }
        return true;
      }
      default -> {
        if (hasValue == hasValueFrom) {
          read.error(at + ": give exactly one of value or valueFrom");
          return false;
        }
        boolean ordering = !op.equals("eq") && !op.equals("ne");
        if (hasValue && ordering && !(value instanceof Number)) {
          read.error(at + ": op " + op + " needs a number in value");
          return false;
        }
        if (hasValue && !isScalar(value)) {
          read.error(at + ": value must be a string, number or boolean");
          return false;
        }
        return true;
      }
    }
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Number || value instanceof Boolean;
  }
}
