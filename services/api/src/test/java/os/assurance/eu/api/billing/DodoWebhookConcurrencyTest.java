package os.assurance.eu.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;

/**
 * Dodo retries any delivery that fails, so each delivery here is retried until it is accepted. What must hold is
 * that a webhook takes effect once, and that the latest event wins however deliveries interleave.
 */
@SpringBootTest(properties = {
    "assurance.dodo.product-team-monthly=prod_team_m",
    "assurance.dodo.product-team-yearly=prod_team_y",
    "assurance.dodo.product-business-monthly=prod_bus_m",
    "assurance.dodo.product-business-yearly=prod_bus_y"
})
class DodoWebhookConcurrencyTest {
  @Autowired DodoWebhookService webhooks;
  @Autowired TenantJpaRepository tenants;
  @Autowired UserJpaRepository users;
  @Autowired TenantSubscriptionJpaRepository subscriptions;
  @Autowired JdbcTemplate jdbc;

  @RepeatedTest(5)
  void concurrentRedeliveriesOfOneWebhookApplyOnce() throws Exception {
    UUID tenantId = newWorkspace();
    String body = event("subscription.active", "2026-10-01T10:00:00Z", tenantId);
    deliverConcurrently(List.of("msg_once", "msg_once", "msg_once", "msg_once", "msg_once", "msg_once"),
        List.of(body, body, body, body, body, body), tenantId);

    assertThat(subscriptions.findById(tenantId).orElseThrow().status()).isEqualTo("ACTIVE");
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where tenant_id = ? and event_type = 'billing.subscription.active'",
        Long.class, tenantId)).isEqualTo(1L);
  }

  @RepeatedTest(10)
  void theLatestEventWinsWhenTwoEventsForAPayingWorkspaceRace() throws Exception {
    UUID tenantId = newWorkspace();
    webhooks.handle("msg_start_" + tenantId,
        event("subscription.active", "2026-10-01T10:00:00Z", tenantId).getBytes(StandardCharsets.UTF_8));
    deliverConcurrently(List.of("msg_renewed", "msg_hold"), List.of(
        event("subscription.renewed", "2026-10-01T10:01:00Z", tenantId),
        event("subscription.on_hold", "2026-10-01T10:05:00Z", tenantId)), tenantId);

    assertThat(subscriptions.findById(tenantId).orElseThrow().status()).isEqualTo("ON_HOLD");
  }

  @RepeatedTest(5)
  void theLatestEventWinsWhenAWorkspaceFirstTwoEventsRace() throws Exception {
    UUID tenantId = newWorkspace();
    deliverConcurrently(List.of("msg_active", "msg_hold"), List.of(
        event("subscription.active", "2026-10-01T10:00:00Z", tenantId),
        event("subscription.on_hold", "2026-10-01T10:05:00Z", tenantId)), tenantId);

    assertThat(subscriptions.findById(tenantId).orElseThrow().status()).isEqualTo("ON_HOLD");
  }

  private void deliverConcurrently(List<String> ids, List<String> bodies, UUID tenantId) throws Exception {
    // Webhook ids are global, so they are made unique per workspace.
    ExecutorService pool = Executors.newFixedThreadPool(ids.size());
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> deliveries = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      String id = ids.get(i) + "_" + tenantId;
      byte[] raw = bodies.get(i).getBytes(StandardCharsets.UTF_8);
      deliveries.add(pool.submit(() -> {
        start.await();
        for (int attempt = 0; ; attempt++) {
          try {
            webhooks.handle(id, raw);
            return null;
          } catch (Exception failed) {
            if (attempt == 20) {
              throw failed;
            }
            Thread.sleep(25);
          }
        }
      }));
    }
    start.countDown();
    for (Future<?> delivery : deliveries) {
      delivery.get();
    }
    pool.shutdown();
  }

  private UUID newWorkspace() {
    UUID tenantId = UUID.randomUUID();
    Instant now = Instant.now();
    tenants.save(new TenantEntity(tenantId, "Billing race " + tenantId, "starter", "EU", now));
    users.save(new UserEntity(UUID.randomUUID(), tenantId, "admin-" + tenantId + "@acme.example", UserRole.ADMIN, now));
    return tenantId;
  }

  private static String event(String type, String timestamp, UUID tenantId) {
    String suffix = tenantId.toString().substring(0, 8);
    return """
        {"type":"%s","timestamp":"%s","data":{"subscription_id":"sub_%s","product_id":"prod_team_m",
         "customer":{"customer_id":"cus_%s"},"next_billing_date":"2026-11-01T10:00:00Z",
         "metadata":{"tenant_id":"%s"}}}""".formatted(type, timestamp, suffix, suffix, tenantId);
  }
}
