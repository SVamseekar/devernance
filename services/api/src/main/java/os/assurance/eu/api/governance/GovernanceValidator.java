package os.assurance.eu.api.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks that a system's declared state and its policy fit together, before anything runs. Pure.
 *
 * <p>Findings:
 * <ul>
 *   <li>POLICY_MISSING: the system has no policy, so every runtime request would be denied.</li>
 *   <li>TOOL_UNGOVERNED: the system has a tool the policy does not map to an action.</li>
 *   <li>TOOL_PROHIBITED: the system has a tool whose action the policy denies.</li>
 *   <li>MODEL_NOT_APPROVED: the system's model is not on the policy's approved list.</li>
 *   <li>TOOL_NOT_DECLARED (warning): the policy maps a tool the system does not declare.</li>
 * </ul>
 */
public final class GovernanceValidator {
  private GovernanceValidator() {
  }

  public static List<GovernanceFinding> validate(String systemName, SystemSpec system, PolicySpec policy) {
    List<GovernanceFinding> findings = new ArrayList<>();
    if (policy == null) {
      findings.add(new GovernanceFinding("POLICY_MISSING", GovernanceFinding.ERROR, systemName, systemName,
          "No GovernancePolicy targets this system. Without one every runtime request is denied."));
      return findings;
    }
    for (String tool : system.tools()) {
      String action = policy.tools().get(tool);
      if (action == null) {
        findings.add(new GovernanceFinding("TOOL_UNGOVERNED", GovernanceFinding.ERROR, systemName, tool,
            "Tool '" + tool + "' is not mapped to an action in the policy (spec.tools). "
                + "Map it and add a rule, or remove the tool."));
        continue;
      }
      Optional<PolicySpec.Rule> rule = policy.ruleFor(action);
      boolean denied = rule.map(r -> r.decision() == DecisionStatus.DENY)
          .orElse(policy.defaultDecision() == DecisionStatus.DENY);
      if (denied) {
        String why = rule.map(r -> r.reason() != null ? r.reason() : "rule " + r.action() + " denies it")
            .orElse("no rule covers it and the default is deny");
        findings.add(new GovernanceFinding("TOOL_PROHIBITED", GovernanceFinding.ERROR, systemName, tool,
            "Tool '" + tool + "' performs " + action + ", which the policy denies (" + why + "). "
                + "Remove the tool or change the policy through review."));
      }
    }
    String model = system.modelRef();
    if (model != null && !policy.approvedModels().isEmpty() && !policy.approvesModel(model)) {
      findings.add(new GovernanceFinding("MODEL_NOT_APPROVED", GovernanceFinding.ERROR, systemName, model,
          "Model " + model + " is not on the policy's approved list (spec.models.approved)."));
    }
    for (String tool : policy.tools().keySet().stream().sorted().toList()) {
      if (!system.tools().contains(tool)) {
        findings.add(new GovernanceFinding("TOOL_NOT_DECLARED", GovernanceFinding.WARNING, systemName, tool,
            "The policy maps tool '" + tool + "', but the system does not declare it (spec.tools)."));
      }
    }
    return findings;
  }
}
