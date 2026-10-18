package os.assurance.eu.api.email;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailConfig {
  @Bean
  EmailSender emailSender(
      Environment environment,
      @Value("${assurance.email.mode:log}") String mode,
      @Value("${assurance.email.from:Devernance <no-reply@localhost>}") String from,
      @Value("${assurance.email.primary.host:}") String primaryHost,
      @Value("${assurance.email.primary.port:587}") int primaryPort,
      @Value("${assurance.email.primary.username:}") String primaryUser,
      @Value("${assurance.email.primary.password:}") String primaryPassword,
      @Value("${assurance.email.fallback.host:}") String fallbackHost,
      @Value("${assurance.email.fallback.port:587}") int fallbackPort,
      @Value("${assurance.email.fallback.username:}") String fallbackUser,
      @Value("${assurance.email.fallback.password:}") String fallbackPassword) {
    if ("log".equalsIgnoreCase(mode)) {
      // Log mode prints verification and reset links, so it must never run on a deployed instance.
      if (environment.acceptsProfiles(Profiles.of("postgres"))) {
        throw new IllegalStateException("assurance.email.mode=log is not allowed with the postgres profile; set smtp");
      }
      return new LoggingEmailSender();
    }
    if (!"smtp".equalsIgnoreCase(mode)) {
      throw new IllegalStateException("assurance.email.mode must be 'log' or 'smtp', was: " + mode);
    }
    if (primaryHost.isBlank()) {
      throw new IllegalStateException("assurance.email.primary.host is required when assurance.email.mode=smtp");
    }
    return new SmtpFailoverEmailSender(
        smtp(primaryHost, primaryPort, primaryUser, primaryPassword),
        fallbackHost.isBlank() ? null : smtp(fallbackHost, fallbackPort, fallbackUser, fallbackPassword),
        from);
  }

  private static JavaMailSenderImpl smtp(String host, int port, String user, String password) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(host);
    sender.setPort(port);
    sender.setUsername(user);
    sender.setPassword(password);
    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.starttls.required", "true");
    props.put("mail.smtp.connectiontimeout", "10000");
    props.put("mail.smtp.timeout", "10000");
    props.put("mail.smtp.writetimeout", "10000");
    return sender;
  }
}
