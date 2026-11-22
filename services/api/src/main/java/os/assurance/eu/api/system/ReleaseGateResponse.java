package os.assurance.eu.api.system;

import java.util.List;
import java.util.UUID;

/**
 * {@code blockers} explain a BLOCKED decision. {@code reviewReasons} list what keeps the system from PASS once
 * nothing blocks it; they are filled in for every decision so a pipeline log always says what to fix next.
 */
public record ReleaseGateResponse(
    UUID systemId,
    ReleaseDecision decision,
    List<String> blockers,
    List<GateControl> controls,
    List<String> reviewReasons) {

  public ReleaseGateResponse(UUID systemId, ReleaseDecision decision, List<String> blockers) {
    this(systemId, decision, blockers, List.of(), List.of());
  }

  public ReleaseGateResponse {
    blockers = blockers == null ? List.of() : List.copyOf(blockers);
    controls = controls == null ? List.of() : List.copyOf(controls);
    reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
  }
}
