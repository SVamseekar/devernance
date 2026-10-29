package os.assurance.eu.api.eval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EvalRunRepository {
  private final EvalRunJpaRepository repository;
  private final TenantContext tenantContext;

  public EvalRunRepository(EvalRunJpaRepository repository, TenantContext tenantContext) {
    this.repository = repository;
    this.tenantContext = tenantContext;
  }

  @Transactional
  public EvalRun save(EvalRun evalRun) {
    return repository.save(new EvalRunEntity(tenantContext.tenantId(), evalRun)).toDomain();
  }

  @Transactional(readOnly = true)
  public Optional<EvalRun> findById(UUID runId) {
    return repository.findByTenantIdAndId(tenantContext.tenantId(), runId)
        .map(EvalRunEntity::toDomain);
  }

  @Transactional(readOnly = true)
  public Optional<EvalRun> findLatestCompleted(UUID systemId) {
    return repository
        .findFirstByTenantIdAndSystemIdAndStatusOrderByCompletedAtDesc(tenantContext.tenantId(), systemId, "completed")
        .map(EvalRunEntity::toDomain);
  }

  /** The 20 most recent completed runs, newest first, for the evidence pack. */
  @Transactional(readOnly = true)
  public List<EvalRun> findRecentCompleted(UUID systemId) {
    return repository
        .findTop20ByTenantIdAndSystemIdAndStatusOrderByCompletedAtDesc(tenantContext.tenantId(), systemId, "completed")
        .stream()
        .map(EvalRunEntity::toDomain)
        .toList();
  }

  @Transactional(readOnly = true)
  public long countByStatus(String status) {
    return repository.countByTenantIdAndStatus(tenantContext.tenantId(), status);
  }

  @Transactional(readOnly = true)
  public List<EvalRun> findQueuedRetryable() {
    return repository.findAllByTenantIdAndStatusOrderByQueuedAtAsc(tenantContext.tenantId(), "queued").stream()
        .filter(run -> run.toDomain().workerAttempts() > 0)
        .map(EvalRunEntity::toDomain)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EvalRun> findDeadLetter() {
    return repository.findAllByTenantIdAndStatusOrderByFailedAtDesc(tenantContext.tenantId(), "failed").stream()
        .map(EvalRunEntity::toDomain)
        .toList();
  }

  @Transactional
  Optional<EvalRun> findByIdForUpdate(UUID runId) {
    return repository.findByTenantIdAndIdForUpdate(tenantContext.tenantId(), runId)
        .map(EvalRunEntity::toDomain);
  }

  @Transactional
  Optional<EvalRunClaim> claimQueuedForExecution(UUID runId) {
    Optional<EvalRunEntity> existing = repository.findByTenantIdAndIdForUpdate(tenantContext.tenantId(), runId);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    EvalRun run = existing.get().toDomain();
    if (!"queued".equals(run.status())) {
      return Optional.of(new EvalRunClaim(run, false));
    }
    return Optional.of(new EvalRunClaim(saveClaimed(existing.get().tenantId(), run), true));
  }

  @Transactional
  Optional<EvalRunEntity> claimNextDispatchable() {
    return repository.findDispatchableForUpdate(Instant.now()).stream()
        .findFirst()
        .map(entity -> repository.save(new EvalRunEntity(entity.tenantId(), claimed(entity.toDomain()))));
  }

  private EvalRun saveClaimed(UUID tenantId, EvalRun queued) {
    return repository.save(new EvalRunEntity(tenantId, claimed(queued))).toDomain();
  }

  private EvalRun claimed(EvalRun queued) {
    return new EvalRun(
        queued.runId(),
        queued.systemId(),
        queued.datasetId(),
        "running",
        queued.dataset(),
        queued.modelVersion(),
        queued.promptVersion(),
        queued.threshold(),
        queued.metrics(),
        ReleaseDecision.REVIEW,
        queued.createdAt(),
        queued.queuedAt(),
        Instant.now(),
        null,
        null,
        queued.workerAttempts() + 1,
        queued.maxAttempts(),
        null);
  }
}
