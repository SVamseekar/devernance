package os.assurance.eu.api.governance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The JSON shapes the governance API returns. docs/API.md describes each field. */
public final class GovernanceViews {
  private GovernanceViews() {
  }

  public record PolicyRef(String name, int version) {
  }

  public record ApprovalView(
      UUID id,
      UUID decisionId,
      UUID systemId,
      String system,
      String action,
      String agent,
      Map<String, Object> resource,
      Map<String, Object> context,
      ApprovalStatus status,
      List<String> approverRoles,
      String reason,
      UUID decidedBy,
      String rationale,
      Instant requestedAt,
      Instant expiresAt,
      Instant decidedAt) {
  }

  public record ExecutionView(ExecutionStatus status, String detail, String reference, Instant recordedAt) {
  }

  public record DecisionView(
      /** Null for a dry run, which is not stored. */
      UUID id,
      String system,
      UUID systemId,
      int systemVersion,
      String action,
      Map<String, Object> resource,
      Map<String, Object> context,
      String model,
      String environment,
      String agent,
      DecisionStatus status,
      /** False when the policy runs in monitor mode: the decision is recorded but must not block. */
      boolean enforced,
      /** The one field a caller acts on: true means the action may run now. */
      boolean authorized,
      String rule,
      String reason,
      PolicyRef policy,
      ApprovalView approval,
      ExecutionView execution,
      /** Signed proof of the authorization for the system that carries the action out. Null unless authorized. */
      String authorizationToken,
      Instant authorizationExpiresAt,
      Instant createdAt) {
  }

  public record TokenVerification(
      boolean valid, String reason, UUID decisionId, String action, Instant expiresAt) {
  }

  public record ResourceView(
      UUID id,
      String kind,
      String name,
      int version,
      boolean active,
      UUID systemId,
      String contentSha256,
      Map<String, Object> spec,
      UUID createdBy,
      Instant createdAt) {
    public static ResourceView of(GovernanceResourceEntity entity) {
      return new ResourceView(entity.id(), entity.kind().manifestKind(), entity.name(), entity.version(),
          entity.active(), entity.systemId(), entity.contentSha256(), entity.spec(), entity.createdBy(),
          entity.createdAt());
    }
  }
}
