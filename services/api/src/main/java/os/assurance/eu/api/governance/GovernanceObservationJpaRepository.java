package os.assurance.eu.api.governance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GovernanceObservationJpaRepository extends JpaRepository<GovernanceObservationEntity, UUID> {
  List<GovernanceObservationEntity> findByTenantIdAndSystemIdAndEnvironmentOrderByCreatedAtDesc(
      UUID tenantId, UUID systemId, String environment, Limit limit);

  /**
   * Every environment that ever reported for the system. Not a window of recent rows: many reports from one
   * environment must never hide another environment's drift.
   */
  @Query("select distinct o.environment from GovernanceObservationEntity o"
      + " where o.tenantId = :tenantId and o.systemId = :systemId order by o.environment")
  List<String> findEnvironments(@Param("tenantId") UUID tenantId, @Param("systemId") UUID systemId);

  List<GovernanceObservationEntity>
      findByTenantIdAndSystemIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
          UUID tenantId, UUID systemId, Instant from, Instant to, Limit limit);
}
