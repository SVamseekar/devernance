package os.assurance.eu.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * One row per paying tenant. Changed only by verified Dodo webhooks. {@link Persistable} makes the first row an
 * INSERT, so two webhooks racing to create it cannot overwrite each other; the loser fails and Dodo redelivers it.
 */
@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscriptionEntity implements Persistable<UUID> {
  @Id
  private UUID tenantId;

  @Column(nullable = false)
  private String planCode;

  @Column(nullable = false)
  private String status;

  @Column(name = "billing_interval")
  private String billingInterval;

  private String dodoCustomerId;
  private String dodoSubscriptionId;
  private Instant currentPeriodEnd;
  private Instant graceUntil;

  @Column(nullable = false)
  private Instant updatedAt;

  @Transient
  private boolean isNew = true;

  protected TenantSubscriptionEntity() {
  }

  public TenantSubscriptionEntity(UUID tenantId) {
    this.tenantId = tenantId;
    this.planCode = PlanCatalog.FREE.name();
    this.status = "NONE";
    this.updatedAt = Instant.EPOCH;
  }

  public UUID tenantId() { return tenantId; }
  public String planCode() { return planCode; }
  public String status() { return status; }
  public String billingInterval() { return billingInterval; }
  public String dodoCustomerId() { return dodoCustomerId; }
  public String dodoSubscriptionId() { return dodoSubscriptionId; }
  public Instant currentPeriodEnd() { return currentPeriodEnd; }
  public Instant graceUntil() { return graceUntil; }
  public Instant updatedAt() { return updatedAt; }

  /** Payment is current. A renewal that arrives late (older period end) never moves the period backwards. */
  public void activate(String plan, String interval, String customerId, String subscriptionId,
      Instant periodEnd, Instant now) {
    this.planCode = plan;
    this.status = "ACTIVE";
    this.billingInterval = interval;
    if (customerId != null) {
      this.dodoCustomerId = customerId;
    }
    if (subscriptionId != null) {
      this.dodoSubscriptionId = subscriptionId;
    }
    if (periodEnd != null && (currentPeriodEnd == null || periodEnd.isAfter(currentPeriodEnd))) {
      this.currentPeriodEnd = periodEnd;
    }
    this.graceUntil = null;
    this.updatedAt = now;
  }

  /** Payment failed: the plan stays until {@code graceUntil}, then the workspace drops to Free. */
  public void hold(Instant graceUntil, Instant now) {
    this.status = "ON_HOLD";
    this.graceUntil = graceUntil;
    this.updatedAt = now;
  }

  /** Cancelled: access continues until the end of the period already paid for. */
  public void cancel(Instant periodEnd, Instant now) {
    this.status = "CANCELLED";
    this.graceUntil = periodEnd;
    this.updatedAt = now;
  }

  @Override
  public UUID getId() {
    return tenantId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    isNew = false;
  }
}
