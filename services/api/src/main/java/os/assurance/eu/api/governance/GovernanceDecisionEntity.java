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
 * The record of one authorization request. Everything needed to explain it later is copied in at decision time:
 * who asked, for what, and which system and policy version answered. Only the execution outcome is added later,
 * and only once.
 */
@Entity
@Table(name = "governance_decisions")
public class GovernanceDecisionEntity {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID systemId;

  @Column(nullable = false)
  private UUID systemResourceId;

  @Column(nullable = false)
  private int systemVersion;

  private UUID policyResourceId;
  private String policyName;
  private Integer policyVersion;

  @Column(nullable = false)
  private String agent;

  private UUID apiKeyId;

  @Column(nullable = false)
  private UUID actorId;

  @Column(nullable = false)
  private String action;

  @Convert(converter = JsonMapConverter.class)
  @Column(name = "resource_json", nullable = false)
  private Map<String, Object> resource;

  @Convert(converter = JsonMapConverter.class)
  @Column(name = "context_json", nullable = false)
  private Map<String, Object> context;

  @Column(name = "request_sha256", nullable = false)
  private String requestSha256;

  private String model;

  @Column(nullable = false)
  private String environment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DecisionStatus status;

  @Column(nullable = false)
  private boolean enforced;

  @Column(nullable = false)
  private String rule;

  @Column(nullable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  private ExecutionStatus executionStatus;

  private String executionDetail;
  private String executionReference;
  private Instant executionRecordedAt;

  @Column(nullable = false)
  private Instant createdAt;

  protected GovernanceDecisionEntity() {
  }

  public GovernanceDecisionEntity(
      UUID id, UUID tenantId, UUID systemId, GovernanceResourceEntity systemResource,
      GovernanceResourceEntity policyResource, String agent, UUID apiKeyId, UUID actorId, String action,
      Map<String, Object> resource, Map<String, Object> context, String requestSha256, String model,
      String environment, DecisionStatus status, boolean enforced, String rule, String reason, Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.systemId = systemId;
    this.systemResourceId = systemResource.id();
    this.systemVersion = systemResource.version();
    if (policyResource != null) {
      this.policyResourceId = policyResource.id();
      this.policyName = policyResource.name();
      this.policyVersion = policyResource.version();
    }
    this.agent = agent;
    this.apiKeyId = apiKeyId;
    this.actorId = actorId;
    this.action = action;
    this.resource = resource;
    this.context = context;
    this.requestSha256 = requestSha256;
    this.model = model;
    this.environment = environment;
    this.status = status;
    this.enforced = enforced;
    this.rule = rule;
    this.reason = reason;
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

  public UUID systemResourceId() {
    return systemResourceId;
  }

  public int systemVersion() {
    return systemVersion;
  }

  public UUID policyResourceId() {
    return policyResourceId;
  }

  public String policyName() {
    return policyName;
  }

  public Integer policyVersion() {
    return policyVersion;
  }

  public String agent() {
    return agent;
  }

  public UUID apiKeyId() {
    return apiKeyId;
  }

  public UUID actorId() {
    return actorId;
  }

  public String action() {
    return action;
  }

  public Map<String, Object> resource() {
    return resource;
  }

  public Map<String, Object> context() {
    return context;
  }

  public String requestSha256() {
    return requestSha256;
  }

  public String model() {
    return model;
  }

  public String environment() {
    return environment;
  }

  public DecisionStatus status() {
    return status;
  }

  public boolean enforced() {
    return enforced;
  }

  public String rule() {
    return rule;
  }

  public String reason() {
    return reason;
  }

  public ExecutionStatus executionStatus() {
    return executionStatus;
  }

  public String executionDetail() {
    return executionDetail;
  }

  public String executionReference() {
    return executionReference;
  }

  public Instant executionRecordedAt() {
    return executionRecordedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public void recordExecution(ExecutionStatus outcome, String detail, String reference, Instant at) {
    this.executionStatus = outcome;
    this.executionDetail = detail;
    this.executionReference = reference;
    this.executionRecordedAt = at;
  }
}
