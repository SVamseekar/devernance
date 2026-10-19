package os.assurance.eu.api.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EmailConfigTest {
  private EmailSender build(String mode, String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return new EmailConfig().emailSender(env, mode, "x@example.com",
        "smtp.example.com", 587, "u", "p", "", 587, "", "");
  }

  @Test
  void logModeIsAllowedLocally() {
    assertThat(build("log")).isInstanceOf(LoggingEmailSender.class);
  }

  @Test
  void logModeIsRefusedWhenDeployedWithPostgresProfile() {
    assertThatThrownBy(() -> build("log", "postgres")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unknownModeFailsClosed() {
    assertThatThrownBy(() -> build("smpt")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void smtpModeNeedsAPrimaryHost() {
    MockEnvironment env = new MockEnvironment();
    assertThatThrownBy(() -> new EmailConfig().emailSender(env, "smtp", "x@example.com", "", 587, "", "", "", 587, "", ""))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void smtpModeBuildsFailoverSenderOffTheRequestThread() {
    EmailSender sender = build("smtp", "postgres");
    assertThat(sender).isInstanceOf(AsyncEmailSender.class);
    assertThat(((AsyncEmailSender) sender).delegate()).isInstanceOf(SmtpFailoverEmailSender.class);
  }
}
