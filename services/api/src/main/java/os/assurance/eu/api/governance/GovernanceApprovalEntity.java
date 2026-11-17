package os.assurance.eu.api.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import os.assurance.eu.api.persistence.StringListConverter;

/** The human approval one REVIEW decision waits for. It is bound to that decision and cannot be reused. */
@Entity
@Table(name = "governance_approvals")
public class GovernanceApprovalEntity {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID decisionId;

  @Column(nullable = false)
  private UUID systemId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ApprovalStatus status;

  @Convert(converter = StringListConverter.class)
  @Column(nullable = false)
  private List<String> approverRoles;

  @Column(nullable = false)
  private String reason;

  private UUID decidedBy;
  private String rationale;

  @Column(nullable = false)
  private Instant requestedAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant decidedAt;

  protected GovernanceApprovalEntity() {
  }

  public GovernanceApprovalEntity(
      UUID id, UUID tenantId, UUID decisionId, UUID systemId, List<String> approverRoles, String reason,
      Instant requestedAt, Instant expiresAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.decisionId = decisionId;
    this.systemId = systemId;
    this.status = ApprovalStatus.PENDING;
    this.approverRoles = approverRoles;
    this.reason = reason;
    this.requestedAt = requestedAt;
    this.expiresAt = expiresAt;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID decisionId() {
    return decisionId;
  }

  public UUID systemId() {
    return systemId;
  }

  /** The stored status. Use {@link #statusAt(Instant)} to see an unanswered request as EXPIRED once it is late. */
  public ApprovalStatus status() {
    return status;
  }

  /**
   * The status at {@code now}. A request nobody answered in time is EXPIRED. An approval also stops being usable
   * at {@code expiresAt}: an approved action that was never carried out must be asked for again.
   */
  public ApprovalStatus statusAt(Instant now) {
    boolean late = !now.isBefore(expiresAt);
    if (late && (status == ApprovalStatus.PENDING || status == ApprovalStatus.APPROVED)) {
      return ApprovalStatus.EXPIRED;
    }
    return status;
  }

  public List<String> approverRoles() {
    return approverRoles;
  }

  public String reason() {
    return reason;
  }

  public UUID decidedBy() {
    return decidedBy;
  }

  public String rationale() {
    return rationale;
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant decidedAt() {
    return decidedAt;
  }

  public void decide(ApprovalStatus outcome, UUID actorId, String note, Instant at) {
    this.status = outcome;
    this.decidedBy = actorId;
    this.rationale = note;
    this.decidedAt = at;
  }
}
