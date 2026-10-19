package os.assurance.eu.api.tenant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the postgres (production) profile while any seeded demo account still
 * accepts the published development password. See audit finding F1. Checked after migrations have run and before
 * the web server starts, so such an instance never answers a login.
 */
@Component
public class DemoCredentialGuard implements SmartInitializingSingleton {
  public static final String DEV_PASSWORD = "dev-local-password-only";
  public static final List<String> SEEDED_EMAILS = List.of(
      "compliance@example.com",
      "engineering@example.com",
      "auditor@example.com",
      "legal@example.com",
      "admin@example.com");

  private final UserJpaRepository users;
  private final Environment environment;
  private final boolean allowDemoPasswords;
  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  public DemoCredentialGuard(
      UserJpaRepository users,
      Environment environment,
      @Value("${assurance.bootstrap.allow-demo-passwords:false}") boolean allowDemoPasswords) {
    this.users = users;
    this.environment = environment;
    this.allowDemoPasswords = allowDemoPasswords;
  }

  @Override
  public void afterSingletonsInstantiated() {
    boolean production = Arrays.asList(environment.getActiveProfiles()).contains("postgres");
    if (!production || allowDemoPasswords) {
      return;
    }
    List<String> offenders = offendingEmails(users, encoder);
    if (!offenders.isEmpty()) {
      throw new IllegalStateException(
          "Refusing to start: seeded accounts still use the published development password: "
              + String.join(", ", offenders)
              + ". Run scripts/disable-seeded-users.sql or rotate these passwords.");
    }
  }

  public static List<String> offendingEmails(UserJpaRepository users, PasswordEncoder encoder) {
    List<String> offenders = new ArrayList<>();
    for (String email : SEEDED_EMAILS) {
      users.findByEmailIgnoreCase(email)
          .filter(user -> user.passwordHash() != null)
          .filter(user -> encoder.matches(DEV_PASSWORD, user.passwordHash()))
          .ifPresent(user -> offenders.add(email));
    }
    return offenders;
  }
}
