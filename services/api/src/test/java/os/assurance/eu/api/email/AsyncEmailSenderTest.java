package os.assurance.eu.api.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AsyncEmailSenderTest {
  private final EmailMessage message = new EmailMessage("a@example.com", "Reset your password", "body");

  @Test
  void returnsBeforeASlowMailServerAnswers() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch delivered = new CountDownLatch(1);
    AsyncEmailSender sender = new AsyncEmailSender(m -> {
      try {
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      delivered.countDown();
    });

    long started = System.nanoTime();
    sender.send(message);
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(500);
    assertThat(delivered.getCount()).isEqualTo(1);

    release.countDown();
    assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
    sender.close();
  }

  @Test
  void aDeliveryFailureDoesNotReachTheCaller() throws Exception {
    CountDownLatch attempted = new CountDownLatch(1);
    AsyncEmailSender sender = new AsyncEmailSender(m -> {
      attempted.countDown();
      throw new IllegalStateException("Email delivery failed on primary and fallback");
    });
    sender.send(message);
    assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
    sender.close();
  }

  @Test
  void closeLetsQueuedMessagesGoOut() throws Exception {
    AtomicReference<String> sent = new AtomicReference<>();
    AsyncEmailSender sender = new AsyncEmailSender(m -> sent.set(m.to()));
    sender.send(message);
    sender.close();
    assertThat(sent.get()).isEqualTo("a@example.com");
  }
}
