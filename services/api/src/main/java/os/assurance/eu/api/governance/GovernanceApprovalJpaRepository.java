package os.assurance.eu.api.governance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GovernanceApprovalJpaRepository extends JpaRepository<GovernanceApprovalEntity, UUID> {
  Optional<GovernanceApprovalEntity> findByDecisionIdAndTenantId(UUID decisionId, UUID tenantId);

  /** Row lock, so two reviewers answering at once cannot both win. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from GovernanceApprovalEntity a where a.id = :id and a.tenantId = :tenantId")
  Optional<GovernanceApprovalEntity> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  List<GovernanceApprovalEntity> findByTenantIdOrderByRequestedAtDesc(UUID tenantId, Limit limit);

  List<GovernanceApprovalEntity> findByTenantIdAndStatusOrderByRequestedAtDesc(
      UUID tenantId, ApprovalStatus status, Limit limit);

  List<GovernanceApprovalEntity> findAllByTenantIdAndDecisionIdIn(UUID tenantId, List<UUID> decisionIds);
}
