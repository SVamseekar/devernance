package os.assurance.eu.api.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateSignals;
import org.springframework.stereotype.Component;

/**
 * Holds the release to the latest completed eval run's own verdict: its pass threshold and the per-metric floors.
 * The system eval score alone is an average and only blocks below the hard threshold.
 */
@Component
public class EvalRunGateSignals implements ReleaseGateSignals {
  private final EvalRunRepository evalRuns;
  private final EvalMetricFloors floors;

  public EvalRunGateSignals(EvalRunRepository evalRuns, EvalMetricFloors floors) {
    this.evalRuns = evalRuns;
    this.floors = floors;
  }

  @Override
  public List<Signal> signals(AiSystem system) {
    return evalRuns.findLatestCompleted(system.id())
        .map(this::signalsFor)
        .orElse(List.of());
  }

  private List<Signal> signalsFor(EvalRun run) {
    List<Signal> out = new ArrayList<>();
    for (String violation : floors.violations(run.metrics())) {
      out.add(Signal.block(violation));
    }
    int threshold = (int) Math.round(run.threshold() * 100);
    if (out.isEmpty() && run.releaseDecision() == ReleaseDecision.BLOCKED) {
      out.add(Signal.block(String.format(Locale.ROOT,
          "Latest eval run (prompt %s) scored below its %d threshold", label(run.promptVersion()), threshold)));
    }
    return out;
  }

  private static String label(String value) {
    return value == null || value.isBlank() ? "unversioned" : value;
  }
}
