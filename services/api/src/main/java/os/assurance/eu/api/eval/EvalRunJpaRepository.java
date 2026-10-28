package os.assurance.eu.api.eval;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EvalRunJpaRepository extends JpaRepository<EvalRunEntity, UUID> {
  Optional<EvalRunEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<EvalRunEntity> findFirstByTenantIdAndSystemIdAndStatusOrderByCompletedAtDesc(
      UUID tenantId, UUID systemId, String status);

  long countByTenantIdAndStatus(UUID tenantId, String status);

  List<EvalRunEntity> findAllByTenantIdAndStatusOrderByQueuedAtAsc(UUID tenantId, String status);

  List<EvalRunEntity> findTop20ByTenantIdAndSystemIdAndStatusOrderByCompletedAtDesc(
      UUID tenantId, UUID systemId, String status);

  List<EvalRunEntity> findAllByTenantIdAndStatusOrderByFailedAtDesc(UUID tenantId, String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from EvalRunEntity e where e.tenantId = :tenantId and e.id = :id")
  Optional<EvalRunEntity> findByTenantIdAndIdForUpdate(UUID tenantId, UUID id);

  @Query(value = """
      select *
      from eval_runs
      where status = 'queued'
        and queued_at <= :queuedAt
      order by queued_at asc
      limit 1
      for update skip locked
      """, nativeQuery = true)
  List<EvalRunEntity> findDispatchableForUpdate(Instant queuedAt);
}
