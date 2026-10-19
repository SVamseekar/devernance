package os.assurance.eu.api.email;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands each message to a small background pool and returns at once. A slow or failing mail server then cannot
 * stretch a request past the dashboard's timeout, and the response time of signup and forgot-password no longer
 * shows whether an email was sent (which would reveal whether an address has an account). Delivery failures are
 * logged; the links in these emails can always be requested again.
 */
public class AsyncEmailSender implements EmailSender, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(AsyncEmailSender.class);
  private static final int THREADS = 2;
  private static final int QUEUE_CAPACITY = 500;

  private final EmailSender delegate;
  private final ThreadPoolExecutor pool;

  public AsyncEmailSender(EmailSender delegate) {
    this.delegate = delegate;
    this.pool = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
          Thread thread = new Thread(runnable, "email-sender");
          thread.setDaemon(true);
          return thread;
        });
  }

  EmailSender delegate() {
    return delegate;
  }

  @Override
  public void send(EmailMessage message) {
    try {
      pool.execute(() -> {
        try {
          delegate.send(message);
        } catch (RuntimeException e) {
          log.error("Email '{}' could not be delivered: {}", message.subject(), e.getMessage());
        }
      });
    } catch (RejectedExecutionException e) {
      log.error("Email queue is full or closed; dropped '{}'", message.subject());
    }
  }

  /** Lets queued messages go out on shutdown, for up to 20 seconds. */
  @Override
  public void close() throws InterruptedException {
    pool.shutdown();
    if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
      log.warn("{} queued emails were not sent before shutdown", pool.getQueue().size());
      pool.shutdownNow();
    }
  }
}
