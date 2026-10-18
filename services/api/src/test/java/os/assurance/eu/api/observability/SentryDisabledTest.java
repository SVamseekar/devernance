package os.assurance.eu.api.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Sentry;
import io.sentry.spring.boot4.SentryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Without SENTRY_DSN (local, tests, unconfigured environments) Sentry is not configured at all. */
@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "spring.datasource.url=jdbc:h2:mem:sentry_disabled;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class SentryDisabledTest {
  @Autowired ApplicationContext context;

  @Test
  void blankDsnLeavesSentryOff() {
    assertThat(context.getBeanNamesForType(SentryProperties.class)).isEmpty();
    assertThat(Sentry.isEnabled()).isFalse();
  }
}
