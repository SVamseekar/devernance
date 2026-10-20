package os.assurance.eu.api.audit;

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
 * The id is the tenant id, assigned up front, so without {@link Persistable} Spring Data would merge a new head:
 * the loser of a creation race would find the winner's row and write a null head hash over an advanced chain.
 * Persisting instead makes the loser fail on the primary key, which {@link AuditChainHeads} expects.
 */
@Entity
@Table(name = "audit_chain_heads")
public class AuditChainHeadEntity implements Persistable<UUID> {
  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "head_hash")
  private String headHash;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Transient
  private boolean isNew = true;

  protected AuditChainHeadEntity() {
  }

  public AuditChainHeadEntity(UUID tenantId, String headHash, Instant updatedAt) {
    this.tenantId = tenantId;
    this.headHash = headHash;
    this.updatedAt = updatedAt;
  }

  public UUID tenantId() { return tenantId; }
  public String headHash() { return headHash; }

  public void advance(String newHead, Instant at) {
    this.headHash = newHead;
    this.updatedAt = at;
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
