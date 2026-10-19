package os.assurance.eu.api.corpus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The date force statuses are judged against: {@code assurance.corpus.as-of} when set (tests, reproducible
 * demos), otherwise today in UTC, read on every call so a long-running instance sees duties come into force.
 */
@Component
public class CorpusAsOf {
  private final LocalDate fixed;
  private final Clock clock;

  public CorpusAsOf(@Value("${assurance.corpus.as-of:}") String asOf, Clock clock) {
    this.fixed = asOf == null || asOf.isBlank() ? null : LocalDate.parse(asOf);
    this.clock = clock;
  }

  public LocalDate today() {
    return fixed != null ? fixed : LocalDate.now(clock.withZone(ZoneOffset.UTC));
  }

  /** Status on {@code asOf}; the stored status only marks provisions that are out of scope. */
  public static ForceStatus statusOn(ForceStatus stored, LocalDate forceFrom, LocalDate asOf) {
    if (stored == ForceStatus.OUT_OF_SCOPE || forceFrom == null) {
      return stored;
    }
    return forceFrom.isAfter(asOf) ? ForceStatus.FUTURE : ForceStatus.IN_FORCE;
  }
}
