package os.assurance.eu.api.governance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GovernanceObservationJpaRepository extends JpaRepository<GovernanceObservationEntity, UUID> {
  List<GovernanceObservationEntity> findByTenantIdAndSystemIdAndEnvironmentOrderByCreatedAtDesc(
      UUID tenantId, UUID systemId, String environment, Limit limit);

  List<GovernanceObservationEntity> findByTenantIdAndSystemIdOrderByCreatedAtDesc(
      UUID tenantId, UUID systemId, Limit limit);

  List<GovernanceObservationEntity>
      findByTenantIdAndSystemIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
          UUID tenantId, UUID systemId, Instant from, Instant to, Limit limit);
}
