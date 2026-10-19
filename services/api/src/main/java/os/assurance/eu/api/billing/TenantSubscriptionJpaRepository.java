package os.assurance.eu.api.billing;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSubscriptionJpaRepository extends JpaRepository<TenantSubscriptionEntity, UUID> {
  Optional<TenantSubscriptionEntity> findByDodoSubscriptionId(String dodoSubscriptionId);

  /** Webhooks for one workspace apply one at a time, so the event-time check sees the latest state. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from TenantSubscriptionEntity s where s.tenantId = :tenantId")
  Optional<TenantSubscriptionEntity> findForUpdate(@Param("tenantId") UUID tenantId);

}
