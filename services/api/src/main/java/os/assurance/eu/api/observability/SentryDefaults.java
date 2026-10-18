package os.assurance.eu.api.observability;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error reporting rules that configuration cannot loosen. Sentry itself stays off unless SENTRY_DSN is set; the
 * environment name comes from SENTRY_ENVIRONMENT.
 */
@Configuration(proxyBeanMethods = false)
class SentryDefaults {

  @Bean
  Sentry.OptionsConfiguration<SentryOptions> assuranceSentryDefaults() {
    return options -> {
      // Evidence, emails, and tenant data must never leave in error reports.
      options.setSendDefaultPii(false);
      options.setTracesSampleRate(0.0);
      // A deliberate HTTP answer (404, 402, 409, ...) is not a fault.
      options.addIgnoredExceptionForType(ResponseStatusException.class);
    };
  }
}
