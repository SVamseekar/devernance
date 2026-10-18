package os.assurance.eu.api.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpFailoverEmailSenderTest {
  private final EmailMessage msg = new EmailMessage("user@example.com", "Subject", "Body");

  @Test
  void usesPrimaryWhenItWorks() {
    JavaMailSender primary = mock(JavaMailSender.class);
    JavaMailSender fallback = mock(JavaMailSender.class);
    new SmtpFailoverEmailSender(primary, fallback, "Devernance <no-reply@example.com>").send(msg);
    verify(primary).send(any(SimpleMailMessage.class));
    verify(fallback, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void fallsBackWhenPrimaryFails() {
    JavaMailSender primary = mock(JavaMailSender.class);
    JavaMailSender fallback = mock(JavaMailSender.class);
    doThrow(new MailSendException("rate limited")).when(primary).send(any(SimpleMailMessage.class));
    new SmtpFailoverEmailSender(primary, fallback, "no-reply@example.com").send(msg);
    verify(fallback).send(any(SimpleMailMessage.class));
  }

  @Test
  void throwsWhenBothFail() {
    JavaMailSender primary = mock(JavaMailSender.class);
    JavaMailSender fallback = mock(JavaMailSender.class);
    doThrow(new MailSendException("a")).when(primary).send(any(SimpleMailMessage.class));
    doThrow(new MailSendException("b")).when(fallback).send(any(SimpleMailMessage.class));
    assertThatThrownBy(() -> new SmtpFailoverEmailSender(primary, fallback, "x@example.com").send(msg))
        .isInstanceOf(IllegalStateException.class);
  }
}
