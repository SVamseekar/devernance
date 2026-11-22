package os.assurance.eu.api.eval;

import java.util.Optional;
import os.assurance.eu.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(
    name = {"assurance.eval.worker.enabled", "assurance.eval.synthetic-metrics.enabled"},
    havingValue = "true")
public class EvalRunQueueWorker {
  private static final Logger LOGGER = LoggerFactory.getLogger(EvalRunQueueWorker.class);

  private final EvalRunRepository evalRuns;
  private final EvalRunWorkerService workerService;
  private final TenantContext tenantContext;
  private final EvalRunMetrics metrics;

  public EvalRunQueueWorker(
      EvalRunRepository evalRuns,
      EvalRunWorkerService workerService,
      TenantContext tenantContext,
      EvalRunMetrics metrics) {
    this.evalRuns = evalRuns;
    this.workerService = workerService;
    this.tenantContext = tenantContext;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${assurance.eval.worker.poll-interval-ms:5000}")
  public void dispatchNextQueuedRun() {
    Optional<EvalRunEntity> dispatchable = evalRuns.claimNextDispatchable();
    if (dispatchable.isEmpty()) {
      return;
    }
    EvalRunEntity run = dispatchable.get();
    metrics.claimed("scheduler");
    try {
      tenantContext.withTenant(run.tenantId(), () -> workerService.executeClaimed(run.toDomain()));
    } catch (ResponseStatusException exception) {
      LOGGER.warn("Eval run {} dispatch failed with status {}", run.id(), exception.getStatusCode());
    } catch (RuntimeException exception) {
      LOGGER.warn("Eval run {} dispatch failed", run.id(), exception);
    }
  }
}
