package os.assurance.eu.api.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails startup of the postgres profile when a required secret is missing, a dev default, or short. Checked once
 * all beans exist and before the web server starts, so a misconfigured instance never answers a request.
 */
@Component
public class ProductionSecretsGuard implements SmartInitializingSingleton {
  static final List<String> REQUIRED = List.of(
      "assurance.audit.chain-secret",
      "assurance.oauth.state-secret",
      "assurance.eval.callback.secret",
      "assurance.auth.key-encryption-secret");
  static final int MIN_LENGTH = 32;

  private final Environment environment;

  public ProductionSecretsGuard(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!Arrays.asList(environment.getActiveProfiles()).contains("postgres")) {
      return;
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (String key : REQUIRED) {
      values.put(key, environment.getProperty(key, ""));
    }
    List<String> violations = violations(values);
    if (!violations.isEmpty()) {
      throw new IllegalStateException("Refusing to start: weak or default secrets for " + violations
          + ". Set each to a random value of at least " + MIN_LENGTH + " characters.");
    }
  }

  public static List<String> violations(Map<String, String> values) {
    List<String> bad = new ArrayList<>();
    for (String key : REQUIRED) {
      String value = values.getOrDefault(key, "");
      if (value == null || value.isBlank() || value.length() < MIN_LENGTH
          || value.startsWith("local-dev") || value.contains("change-me")) {
        bad.add(key);
      }
    }
    return bad;
  }
}
