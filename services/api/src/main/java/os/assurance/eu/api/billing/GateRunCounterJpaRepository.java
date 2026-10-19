package os.assurance.eu.api.billing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GateRunCounterJpaRepository extends JpaRepository<GateRunCounterEntity, GateRunCounterEntity.Key> {
  /**
   * Counts one run if the plan still allows it. A single UPDATE, so the limit check and the increment cannot
   * interleave between parallel pipelines. A negative limit means unlimited. Returns 0 when the limit is reached.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update GateRunCounterEntity c set c.runs = c.runs + 1"
      + " where c.tenantId = :tenantId and c.period = :period and (:limit < 0 or c.runs < :limit)")
  int incrementWithin(@Param("tenantId") UUID tenantId, @Param("period") String period, @Param("limit") int limit);

  @Query("select coalesce(sum(c.runs), 0) from GateRunCounterEntity c where c.tenantId = :tenantId and c.period = :period")
  int runsIn(@Param("tenantId") UUID tenantId, @Param("period") String period);
}
