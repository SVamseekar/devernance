package os.assurance.eu.api.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.GateRecalculator;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import os.assurance.eu.api.workflow.WorkflowTrigger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvalRunCompletionService {
  private static final List<String> SCORED_METRIC_KEYS =
      List.of("faithfulness", "relevance", "safetyRefusal", "biasSlicePassRate");

  private final AiSystemRepository systems;
  private final EvalRunRepository evalRuns;
  private final ReleaseGateService releaseGateService;
  private final AuditService auditService;
  private final EvalRunMetrics evalRunMetrics;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final GateRecalculator gateRecalculator;
  private final EvalMetricFloors floors;

  public EvalRunCompletionService(
      AiSystemRepository systems,
      EvalRunRepository evalRuns,
      ReleaseGateService releaseGateService,
      AuditService auditService,
      EvalRunMetrics evalRunMetrics,
      ApprovalWorkflowService approvalWorkflowService,
      GateRecalculator gateRecalculator,
      EvalMetricFloors floors) {
    this.systems = systems;
    this.evalRuns = evalRuns;
    this.releaseGateService = releaseGateService;
    this.auditService = auditService;
    this.evalRunMetrics = evalRunMetrics;
    this.approvalWorkflowService = approvalWorkflowService;
    this.gateRecalculator = gateRecalculator;
    this.floors = floors;
  }

  @Transactional
  public EvalRun complete(UUID runId, Map<String, Object> metrics, String source) {
    EvalRun existing = evalRuns.findByIdForUpdate(runId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval run not found"));
    if ("completed".equals(existing.status())) {
      if (Objects.equals(existing.metrics(), metrics)) {
        return existing;
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Eval run is already completed");
    }
    if ("failed".equals(existing.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Eval run has failed");
    }
    AiSystem system = systems.findById(existing.systemId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    int evalScore = score(metrics, existing.threshold());
    ReleaseDecision runDecision = evalScore >= toPercent(existing.threshold()) && floors.violations(metrics).isEmpty()
        ? ReleaseDecision.PASS
        : ReleaseDecision.BLOCKED;
    int previousScore = system.evalScore();
    EvalRun completed = evalRuns.save(new EvalRun(
        existing.runId(),
        existing.systemId(),
        existing.datasetId(),
        "completed",
        existing.dataset(),
        existing.modelVersion(),
        existing.promptVersion(),
        existing.threshold(),
        metrics,
        runDecision,
        existing.createdAt(),
        existing.queuedAt(),
        existing.startedAt(),
        Instant.now(),
        null,
        existing.workerAttempts(),
        existing.maxAttempts(),
        null));
    AiSystem updated = saveSystemWithEvalScore(system, evalScore);
    gateRecalculator.recalculate(system.id());
    updated = systems.findById(system.id()).orElse(updated);
    auditService.append(
        system.id(),
        "eval_run.completed",
        "eval_run",
        runId.toString(),
        Map.of(
            "source", source,
            "evalScore", evalScore,
            "runDecision", completed.releaseDecision(),
            "releaseDecision", updated.releaseDecision()));
    evalRunMetrics.completed(source);
    // Only a real regression asks people to review again; an improving run would otherwise reopen sign-off.
    if (runDecision != ReleaseDecision.PASS || (previousScore > 0 && evalScore < previousScore)) {
      approvalWorkflowService.openCycle(updated, WorkflowTrigger.EVAL_REGRESSION);
    }
    return completed;
  }

  private int score(Map<String, Object> metrics, double threshold) {
    List<Double> scoredMetrics = SCORED_METRIC_KEYS.stream()
        .map(metrics::get)
        .filter(Number.class::isInstance)
        .map(Number.class::cast)
        .map(Number::doubleValue)
        .toList();
    if (scoredMetrics.size() != SCORED_METRIC_KEYS.size()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Eval metrics must include numeric faithfulness, relevance, safetyRefusal, and biasSlicePassRate");
    }
    double average = scoredMetrics.stream().mapToDouble(Double::doubleValue).average().orElse(threshold);
    return Math.max(0, Math.min(100, (int) Math.round(average * 100)));
  }

  private int toPercent(double threshold) {
    return (int) Math.round(threshold * 100);
  }

  private AiSystem saveSystemWithEvalScore(AiSystem system, int evalScore) {
    AiSystem draft = new AiSystem(
        system.id(),
        system.name(),
        system.owner(),
        system.purpose(),
        system.riskClass(),
        system.riskBasis(),
        system.deploymentRegion(),
        system.evidenceCoverage(),
        evalScore,
        system.dataContractStatus(),
        system.releaseDecision(),
        system.openGaps(),
        system.vendorName(),
        system.modelName(),
        system.modelVersion(),
        system.dataSources(),
        system.sector(),
        system.decisionImpact(),
        system.affectedUsers(),
        system.createdAt(),
        Instant.now());
    ReleaseDecision decision = releaseGateService.calculate(draft).decision();
    return systems.save(new AiSystem(
        draft.id(),
        draft.name(),
        draft.owner(),
        draft.purpose(),
        draft.riskClass(),
        draft.riskBasis(),
        draft.deploymentRegion(),
        draft.evidenceCoverage(),
        draft.evalScore(),
        draft.dataContractStatus(),
        decision,
        draft.openGaps(),
        draft.vendorName(),
        draft.modelName(),
        draft.modelVersion(),
        draft.dataSources(),
        draft.sector(),
        draft.decisionImpact(),
        draft.affectedUsers(),
        draft.createdAt(),
        draft.updatedAt()));
  }
}
