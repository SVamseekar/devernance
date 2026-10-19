package os.assurance.eu.api.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CorpusAsOfTest {
  private static final LocalDate ANNEX_III = LocalDate.of(2027, 12, 2);

  @Test
  void aStoredFutureDutyIsInForceOnceItsDateHasPassed() {
    assertThat(CorpusAsOf.statusOn(ForceStatus.FUTURE, ANNEX_III, LocalDate.of(2027, 12, 1)))
        .isEqualTo(ForceStatus.FUTURE);
    assertThat(CorpusAsOf.statusOn(ForceStatus.FUTURE, ANNEX_III, LocalDate.of(2027, 12, 2)))
        .isEqualTo(ForceStatus.IN_FORCE);
  }

  @Test
  void outOfScopeStaysOutOfScope() {
    assertThat(CorpusAsOf.statusOn(ForceStatus.OUT_OF_SCOPE, ANNEX_III, LocalDate.of(2030, 1, 1)))
        .isEqualTo(ForceStatus.OUT_OF_SCOPE);
  }

  @Test
  void todayFollowsTheClockUnlessPinned() {
    Clock clock = Clock.fixed(Instant.parse("2027-12-01T23:30:00Z"), ZoneOffset.UTC);
    assertThat(new CorpusAsOf("", clock).today()).isEqualTo(LocalDate.of(2027, 12, 1));
    assertThat(new CorpusAsOf("2026-09-23", clock).today()).isEqualTo(LocalDate.of(2026, 9, 23));
  }
}
