package os.assurance.eu.api.eval;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.system.ReleaseDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Completes queued eval runs with generated placeholder metrics. Nothing is measured, so it is off unless
 * {@code assurance.eval.synthetic-metrics.enabled} is set (demos and tests); real results arrive through the signed
 * result callback. Generated metrics carry {@code "synthetic": true} so they are never mistaken for a measurement.
 */
@Service
public class EvalRunWorkerService {
  static final String DISABLED = "No eval worker runs here: report measured results with the signed "
      + "PATCH /api/v1/eval-runs/{runId}/result callback";

  private final EvalDatasetRepository datasets;
  private final EvalRunRepository evalRuns;
  private final EvalRunCompletionService completionService;
  private final AuditService auditService;
  private final EvalRunMetrics metrics;
  private final boolean syntheticMetrics;

  public EvalRunWorkerService(
      EvalDatasetRepository datasets,
      EvalRunRepository evalRuns,
      EvalRunCompletionService completionService,
      AuditService auditService,
      EvalRunMetrics metrics,
      @Value("${assurance.eval.synthetic-metrics.enabled:false}") boolean syntheticMetrics) {
    this.datasets = datasets;
    this.evalRuns = evalRuns;
    this.completionService = completionService;
    this.auditService = auditService;
    this.metrics = metrics;
    this.syntheticMetrics = syntheticMetrics;
  }

  public boolean syntheticMetricsEnabled() {
    return syntheticMetrics;
  }

  public EvalRun execute(UUID runId) {
    if (!syntheticMetrics) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, DISABLED);
    }
    EvalRunClaim claim = evalRuns.claimQueuedForExecution(runId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval run not found"));
    EvalRun run = claim.run();
    if (!claim.claimed() && "completed".equals(run.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Eval run is already completed");
    }
    if (!claim.claimed()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Eval run is not queued");
    }
    metrics.claimed("manual");
    return executeClaimed(run);
  }

  EvalRun executeClaimed(EvalRun running) {
    if (!syntheticMetrics) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, DISABLED);
    }
    if (!"running".equals(running.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Eval run is not running");
    }
    if (running.datasetId() == null) {
      EvalRun failed = recordTerminalFailure(running, "Eval dataset is not registered");
      auditService.append(
          failed.systemId(),
          "eval_run.failed",
          "eval_run",
          running.runId().toString(),
          Map.of(
              "attempts", failed.workerAttempts(),
              "maxAttempts", failed.maxAttempts(),
              "failureReason", failed.failureReason()));
      metrics.failed("dataset_not_registered");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eval dataset is not registered");
    }
    EvalDataset dataset = datasets.findById(running.datasetId())
        .orElseGet(() -> {
          EvalRun failed = recordTerminalFailure(running, "Eval dataset is not registered");
          auditService.append(
              failed.systemId(),
              "eval_run.failed",
              "eval_run",
              running.runId().toString(),
              Map.of(
                  "attempts", failed.workerAttempts(),
                  "maxAttempts", failed.maxAttempts(),
                  "failureReason", failed.failureReason()));
          metrics.failed("dataset_not_registered");
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eval dataset is not registered");
        });
    auditService.append(
        running.systemId(),
        "eval_run.running",
        "eval_run",
        running.runId().toString(),
        Map.of("dataset", dataset.name(), "datasetVersion", dataset.version(), "sampleCount", dataset.sampleCount()));
    try {
      return completionService.complete(running.runId(), calculateMetrics(running, dataset), "synthetic-worker");
    } catch (RuntimeException exception) {
      EvalRun failed = recordFailure(running, exception);
      auditService.append(
          failed.systemId(),
          "eval_run.failed",
          "eval_run",
          running.runId().toString(),
          Map.of(
              "attempts", failed.workerAttempts(),
              "maxAttempts", failed.maxAttempts(),
              "failureReason", failed.failureReason()));
      metrics.failed(failed.status());
      throw exception;
    }
  }

  EvalRun recordFailure(EvalRun running, RuntimeException exception) {
    boolean exhausted = running.workerAttempts() >= running.maxAttempts();
    Instant now = Instant.now();
    return evalRuns.save(new EvalRun(
        running.runId(),
        running.systemId(),
        running.datasetId(),
        exhausted ? "failed" : "queued",
        running.dataset(),
        running.modelVersion(),
        running.promptVersion(),
        running.threshold(),
        running.metrics(),
        ReleaseDecision.REVIEW,
        running.createdAt(),
        exhausted ? running.queuedAt() : now.plusSeconds(30),
        running.startedAt(),
        null,
        now,
        running.workerAttempts(),
        running.maxAttempts(),
        failureReason(exception)));
  }

  private EvalRun recordTerminalFailure(EvalRun queued, String failureReason) {
    Instant now = Instant.now();
    return evalRuns.save(new EvalRun(
        queued.runId(),
        queued.systemId(),
        queued.datasetId(),
        "failed",
        queued.dataset(),
        queued.modelVersion(),
        queued.promptVersion(),
        queued.threshold(),
        queued.metrics(),
        ReleaseDecision.REVIEW,
        queued.createdAt(),
        queued.queuedAt(),
        queued.startedAt(),
        null,
        now,
        queued.workerAttempts(),
        queued.maxAttempts(),
        failureReason));
  }

  private String failureReason(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message.length() > 2048 ? message.substring(0, 2048) : message;
  }

  private Map<String, Object> calculateMetrics(EvalRun run, EvalDataset dataset) {
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("faithfulness", metric(run, dataset, "faithfulness", 0.76, 0.19));
    metrics.put("relevance", metric(run, dataset, "relevance", 0.78, 0.18));
    metrics.put("safetyRefusal", metric(run, dataset, "safetyRefusal", 0.82, 0.15));
    metrics.put("biasSlicePassRate", metric(run, dataset, "biasSlicePassRate", 0.73, 0.20));
    metrics.put("latencyP95Ms", 900 + bucket(run, dataset, "latency", 1500));
    metrics.put("costUsd", Math.round((1.25 + bucket(run, dataset, "cost", 700) / 100.0) * 100.0) / 100.0);
    metrics.put("sampleCount", dataset.sampleCount());
    metrics.put("goldenDataset", dataset.golden());
    metrics.put("synthetic", true);
    return metrics;
  }

  private double metric(EvalRun run, EvalDataset dataset, String key, double floor, double spread) {
    double value = floor + (bucket(run, dataset, key, 1000) / 1000.0 * spread);
    return Math.round(value * 100.0) / 100.0;
  }

  private int bucket(EvalRun run, EvalDataset dataset, String key, int bound) {
    return Math.floorMod(
        (run.runId().toString()
            + dataset.id()
            + run.modelVersion()
            + run.promptVersion()
            + key).hashCode(),
        bound);
  }
}
