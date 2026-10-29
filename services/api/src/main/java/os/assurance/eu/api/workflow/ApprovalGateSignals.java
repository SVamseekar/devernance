package os.assurance.eu.api.workflow;

import java.util.ArrayList;
import java.util.List;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.ReleaseGateSignals;
import os.assurance.eu.api.system.RiskClass;
import org.springframework.stereotype.Component;

/**
 * Makes sign-off part of the release decision. A rejected cycle blocks every risk class until a later cycle is
 * approved, so a new eval run cannot erase it by opening a fresh cycle. A high-risk system also waits in REVIEW while
 * any cycle is open (EU AI Act Art. 14 oversight; the reviewers are people, not the gate).
 */
@Component
public class ApprovalGateSignals implements ReleaseGateSignals {
  private final ApprovalWorkflowRepository workflows;

  public ApprovalGateSignals(ApprovalWorkflowRepository workflows) {
    this.workflows = workflows;
  }

  @Override
  public List<Signal> signals(AiSystem system) {
    List<ApprovalWorkflow> cycles = workflows.findAllBySystemId(system.id());
    List<Signal> out = new ArrayList<>();
    // Newest first: the latest decided cycle says whether people last rejected or approved this system.
    for (ApprovalWorkflow cycle : cycles) {
      if (cycle.status() == WorkflowStatus.APPROVED) {
        break;
      }
      if (cycle.status() == WorkflowStatus.REJECTED) {
        out.add(Signal.block("Approval rejected at " + rejectedStage(cycle) + " (" + cycle.trigger()
            + " cycle); a later cycle must be approved"));
        break;
      }
    }
    if (system.riskClass() == RiskClass.HIGH) {
      cycles.stream()
          .filter(cycle -> cycle.status() == WorkflowStatus.OPEN)
          .findFirst()
          .ifPresent(open -> out.add(Signal.review("Approval pending: " + pendingStage(open) + " (" + open.trigger()
              + " cycle)")));
    }
    return out;
  }

  private static String rejectedStage(ApprovalWorkflow cycle) {
    return cycle.stages().stream()
        .filter(stage -> stage.status() == StageStatus.REJECTED)
        .map(stage -> stage.stageType().name())
        .findFirst()
        .orElse("an approval stage");
  }

  private static String pendingStage(ApprovalWorkflow cycle) {
    return cycle.stages().stream()
        .filter(stage -> stage.status() == StageStatus.PENDING)
        .min((left, right) -> Integer.compare(left.stageOrder(), right.stageOrder()))
        .map(stage -> stage.stageType().name())
        .orElse("sign-off");
  }
}
