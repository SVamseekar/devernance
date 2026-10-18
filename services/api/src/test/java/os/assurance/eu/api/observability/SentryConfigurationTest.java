package os.assurance.eu.api.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * With SENTRY_DSN set (production): Sentry runs, but never with personal data or traces, and deliberate HTTP
 * answers (ResponseStatusException) are not reported. The environment comes from SENTRY_ENVIRONMENT.
 */
@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "spring.datasource.url=jdbc:h2:mem:sentry_config;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "sentry.dsn=https://publickey@o0.ingest.sentry.io/0",
    "sentry.environment=production",
    "sentry.send-default-pii=true",
    "sentry.traces-sample-rate=1.0"
})
class SentryConfigurationTest {

  @AfterAll
  static void closeSentry() {
    Sentry.close();
  }

  @Test
  void dsnEnablesSentryButPersonalDataAndTracesStayOffEvenIfConfigured() {
    SentryOptions options = ScopesAdapter.getInstance().getOptions();
    assertThat(Sentry.isEnabled()).isTrue();
    assertThat(options.isSendDefaultPii()).isFalse();
    assertThat(options.getTracesSampleRate()).isEqualTo(0.0);
    assertThat(options.getEnvironment()).isEqualTo("production");
  }

  @Test
  void deliberateHttpErrorResponsesAreNotReported() {
    assertThat(ScopesAdapter.getInstance().getOptions().getIgnoredExceptionsForType())
        .contains(ResponseStatusException.class);
  }
}
