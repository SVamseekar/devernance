package os.assurance.eu.api.system;

import java.util.List;

/**
 * A source of release-gate findings that lives outside the system package (approvals, eval runs). Each finding
 * either blocks the release or sends it to review, with a sentence a CI log can show as-is.
 */
public interface ReleaseGateSignals {

  enum Level { BLOCK, REVIEW }

  record Signal(Level level, String message) {
    public static Signal block(String message) {
      return new Signal(Level.BLOCK, message);
    }

    public static Signal review(String message) {
      return new Signal(Level.REVIEW, message);
    }
  }

  List<Signal> signals(AiSystem system);
}
