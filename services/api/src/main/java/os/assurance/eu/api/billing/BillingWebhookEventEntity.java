package os.assurance.eu.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * One row per webhook id already processed, so a redelivery changes nothing. Payloads are hashed, not stored.
 * {@link Persistable} makes saving a new row an INSERT, so two concurrent deliveries of one webhook cannot both pass:
 * the second fails on the primary key instead of merging over the first.
 */
@Entity
@Table(name = "billing_webhook_events")
public class BillingWebhookEventEntity implements Persistable<String> {
  @Id
  private String webhookId;

  @Column(nullable = false)
  private String eventType;

  @Column(name = "payload_sha256", nullable = false)
  private String payloadSha256;

  @Column(nullable = false)
  private Instant receivedAt;

  @Transient
  private boolean isNew = true;

  protected BillingWebhookEventEntity() {
  }

  public BillingWebhookEventEntity(String webhookId, String eventType, String payloadSha256, Instant receivedAt) {
    this.webhookId = webhookId;
    this.eventType = eventType;
    this.payloadSha256 = payloadSha256;
    this.receivedAt = receivedAt;
  }
  @Override
  public String getId() {
    return webhookId;
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
