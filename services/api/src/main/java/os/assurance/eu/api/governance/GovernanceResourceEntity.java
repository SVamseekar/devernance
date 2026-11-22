package os.assurance.eu.api.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.persistence.JsonMapConverter;

/**
 * One immutable version of an applied manifest. A change never edits a row: it adds the next version and marks
 * the previous one inactive, so a decision can always point at the exact policy text that produced it.
 */
@Entity
@Table(name = "governance_resources")
public class GovernanceResourceEntity {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID systemId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private GovernanceResourceKind kind;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private int version;

  @Convert(converter = JsonMapConverter.class)
  @Column(name = "spec_json", nullable = false)
  private Map<String, Object> spec;

  @Column(name = "content_sha256", nullable = false)
  private String contentSha256;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private UUID createdBy;

  @Column(nullable = false)
  private Instant createdAt;

  protected GovernanceResourceEntity() {
  }

  public GovernanceResourceEntity(
      UUID id, UUID tenantId, UUID systemId, GovernanceResourceKind kind, String name, int version,
      Map<String, Object> spec, String contentSha256, UUID createdBy, Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.systemId = systemId;
    this.kind = kind;
    this.name = name;
    this.version = version;
    this.spec = spec;
    this.contentSha256 = contentSha256;
    this.active = true;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID systemId() {
    return systemId;
  }

  public GovernanceResourceKind kind() {
    return kind;
  }

  public String name() {
    return name;
  }

  public int version() {
    return version;
  }

  public Map<String, Object> spec() {
    return spec;
  }

  public String contentSha256() {
    return contentSha256;
  }

  public boolean active() {
    return active;
  }

  public UUID createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public void supersede() {
    this.active = false;
  }
}
