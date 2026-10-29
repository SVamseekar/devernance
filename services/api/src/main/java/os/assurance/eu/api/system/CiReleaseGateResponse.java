package os.assurance.eu.api.system;

import java.util.List;
import java.util.UUID;

/**
 * Machine-friendly release-gate payload for CI/CD bots (Part 8).
 *
 * <p>CLI exit-code mapping (see {@code scripts/ci-release-gate.sh}):
 * PASS → 0, BLOCKED → 1, REVIEW → 2. HTTP transport errors use other non-zero codes.
 */
public record CiReleaseGateResponse(
    UUID systemId,
    String systemName,
    ReleaseDecision decision,
    List<String> blockers,
    /** What keeps the system from PASS once nothing blocks it. */
    List<String> reviewReasons,
    int evalScore,
    int evidenceCoverage,
    DataContractStatus dataContractStatus,
    RiskClass riskClass,
    /** Suggested process exit code for CI scripts (0/1/2). */
    int exitCode,
    /** Optional short human-readable summary for logs. */
    String content) {

  public CiReleaseGateResponse {
    blockers = blockers == null ? List.of() : List.copyOf(blockers);
    reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
  }

  public static int exitCodeFor(ReleaseDecision decision) {
    if (decision == null) {
      return 1;
    }
    return switch (decision) {
      case PASS -> 0;
      case BLOCKED -> 1;
      case REVIEW -> 2;
    };
  }

  public static CiReleaseGateResponse from(AiSystem system, ReleaseGateResponse gate) {
    ReleaseDecision decision = gate.decision();
    String content = switch (decision) {
      case PASS -> "Release gate PASS — no blockers.";
      case REVIEW -> "Release gate REVIEW — deploy with caution; manual review recommended.";
      case BLOCKED -> "Release gate BLOCKED — do not deploy.";
    };
    if (decision != ReleaseDecision.PASS && !gate.blockers().isEmpty()) {
      content = content + " Blockers: " + String.join("; ", gate.blockers());
    }
    if (decision == ReleaseDecision.REVIEW && !gate.reviewReasons().isEmpty()) {
      content = content + " Review: " + String.join("; ", gate.reviewReasons());
    }
    return new CiReleaseGateResponse(
        system.id(),
        system.name(),
        decision,
        gate.blockers(),
        gate.reviewReasons(),
        system.evalScore(),
        system.evidenceCoverage(),
        system.dataContractStatus(),
        system.riskClass(),
        exitCodeFor(decision),
        content);
  }
}
