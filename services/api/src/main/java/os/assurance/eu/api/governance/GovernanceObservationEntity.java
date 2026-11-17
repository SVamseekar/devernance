package os.assurance.eu.api.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.persistence.JsonMapConverter;

/** What one deployment reported it is really running, at one moment. Never edited. */
@Entity
@Table(name = "governance_observations")
public class GovernanceObservationEntity {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID systemId;

  @Column(nullable = false)
  private String environment;

  @Convert(converter = JsonMapConverter.class)
  @Column(name = "observed_json", nullable = false)
  private Map<String, Object> observed;

  /** Whether it matched the approved state when it was reported. The live answer is recomputed on every read. */
  @Column(nullable = false)
  private boolean inSync;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private UUID reportedBy;

  @Column(nullable = false)
  private Instant createdAt;

  protected GovernanceObservationEntity() {
  }

  public GovernanceObservationEntity(
      UUID id, UUID tenantId, UUID systemId, String environment, Map<String, Object> observed, boolean inSync,
      String source, UUID reportedBy, Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.systemId = systemId;
    this.environment = environment;
    this.observed = observed;
    this.inSync = inSync;
    this.source = source;
    this.reportedBy = reportedBy;
    this.createdAt = createdAt;
  }

  public UUID id() {
    return id;
  }

  public UUID systemId() {
    return systemId;
  }

  public String environment() {
    return environment;
  }

  public Map<String, Object> observed() {
    return observed;
  }

  public boolean inSync() {
    return inSync;
  }

  public String source() {
    return source;
  }

  public UUID reportedBy() {
    return reportedBy;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
