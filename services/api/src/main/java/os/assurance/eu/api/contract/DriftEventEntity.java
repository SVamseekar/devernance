package os.assurance.eu.api.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drift_events")
public class DriftEventEntity {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID contractId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DriftSeverity severity;

  private String field;

  @Column(nullable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DriftStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(length = 2048)
  private String resolutionNote;

  private UUID resolvedBy;

  private Instant resolvedAt;

  protected DriftEventEntity() {
  }

  public DriftEventEntity(UUID tenantId, DriftEvent event) {
    this.id = event.id();
    this.tenantId = tenantId;
    this.contractId = event.contractId();
    this.severity = event.severity();
    this.field = event.field();
    this.description = event.description();
    this.status = event.status();
    this.createdAt = event.createdAt();
    this.updatedAt = event.updatedAt();
    this.resolutionNote = event.resolutionNote();
    this.resolvedBy = event.resolvedBy();
    this.resolvedAt = event.resolvedAt();
  }

  public DriftEvent toDomain() {
    return new DriftEvent(id, contractId, severity, field, description, status, createdAt, updatedAt,
        resolutionNote, resolvedBy, resolvedAt);
  }
}
