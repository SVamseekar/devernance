package os.assurance.eu.api.contract;

import java.time.Instant;
import java.util.UUID;

public record DriftEvent(
    UUID id,
    UUID contractId,
    DriftSeverity severity,
    String field,
    String description,
    DriftStatus status,
    Instant createdAt,
    Instant updatedAt,
    /** Why the event was acknowledged or resolved; required for both. */
    String resolutionNote,
    UUID resolvedBy,
    Instant resolvedAt) {

  public DriftEvent(UUID id, UUID contractId, DriftSeverity severity, String field, String description,
      DriftStatus status, Instant createdAt, Instant updatedAt) {
    this(id, contractId, severity, field, description, status, createdAt, updatedAt, null, null, null);
  }
}
