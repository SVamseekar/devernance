package os.assurance.eu.api.governance;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GovernanceDecisionJpaRepository
    extends JpaRepository<GovernanceDecisionEntity, UUID>, JpaSpecificationExecutor<GovernanceDecisionEntity> {
  Optional<GovernanceDecisionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Row lock, so two reports of the same execution, or a report racing an approval, take turns. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from GovernanceDecisionEntity d where d.id = :id and d.tenantId = :tenantId")
  Optional<GovernanceDecisionEntity> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  List<GovernanceDecisionEntity> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

  List<GovernanceDecisionEntity>
      findByTenantIdAndSystemIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
          UUID tenantId, UUID systemId, Instant from, Instant to, org.springframework.data.domain.Limit limit);

  long countByTenantIdAndSystemIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      UUID tenantId, UUID systemId, Instant from, Instant to);

  long countByTenantIdAndSystemIdAndStatusAndCreatedAtGreaterThanEqual(
      UUID tenantId, UUID systemId, DecisionStatus status, Instant from);
}
