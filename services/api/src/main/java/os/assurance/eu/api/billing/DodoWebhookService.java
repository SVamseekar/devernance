package os.assurance.eu.api.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;

/**
 * Applies verified Dodo webhooks. Webhooks are the only way a paid plan becomes active. Redeliveries are no-ops
 * (keyed by webhook id), and events stamped earlier than the stored state are ignored, so out-of-order delivery
 * converges on the newest state.
 */
@Service
public class DodoWebhookService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DodoWebhookService.class);
  static final Duration PAYMENT_GRACE = Duration.ofDays(7);

  private final BillingWebhookEventJpaRepository events;
  private final TenantSubscriptionJpaRepository subscriptions;
  private final TenantJpaRepository tenants;
  private final UserJpaRepository users;
  private final BillingProducts products;
  private final AuditService audit;
  private final TenantContext tenantContext;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public DodoWebhookService(BillingWebhookEventJpaRepository events, TenantSubscriptionJpaRepository subscriptions,
      TenantJpaRepository tenants, UserJpaRepository users, BillingProducts products, AuditService audit,
      TenantContext tenantContext, ObjectMapper objectMapper, Clock clock) {
    this.events = events;
    this.subscriptions = subscriptions;
    this.tenants = tenants;
    this.users = users;
    this.products = products;
    this.audit = audit;
    this.tenantContext = tenantContext;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public void handle(String webhookId, byte[] rawBody) throws Exception {
    if (events.existsById(webhookId)) {
      return; // redelivery
    }
    JsonNode root = objectMapper.readTree(rawBody);
    String type = root.path("type").asText();
    Instant now = clock.instant();
    // Flushed now: a concurrent delivery of the same id waits here and then fails on the key, before doing any work.
    events.saveAndFlush(new BillingWebhookEventEntity(webhookId, type, sha256Hex(rawBody), now));
    JsonNode data = root.path("data");
    String tenantIdText = data.path("metadata").path("tenant_id").asText("");
    if (tenantIdText.isBlank() || !type.startsWith("subscription.")) {
      return; // payments, refunds and disputes are recorded by id only; nothing to change
    }
    UUID tenantId;
    try {
      tenantId = UUID.fromString(tenantIdText);
    } catch (IllegalArgumentException e) {
      return;
    }
    if (!tenants.existsById(tenantId)) {
      return; // acknowledge so Dodo stops retrying
    }
    BillingProducts.PlanAndInterval pi = products.lookup(data.path("product_id").asText());
    if (pi == null) {
      // Most likely a missing or renamed DODO_PRODUCT_* setting. Do not mark the event processed, so a resend
      // from the Dodo dashboard works once the setting is fixed.
      log.error("Billing webhook {} ({}) names product '{}' that is not configured; not applied",
          webhookId, type, data.path("product_id").asText());
      events.deleteById(webhookId);
      return;
    }
    // Locked before anything else reads this row: a row loaded earlier in the transaction would be returned from the
    // persistence context with its stale state, and a concurrent event's change would be lost.
    TenantSubscriptionEntity sub = subscriptions.findForUpdate(tenantId)
        .orElseGet(() -> new TenantSubscriptionEntity(tenantId));
    // A subscription belongs to exactly one workspace. An event that names another workspace, or a different
    // subscription id than the one already bound, is acknowledged but ignored.
    String subscriptionId = textOrNull(data.path("subscription_id"));
    if (subscriptionId != null) {
      var owner = subscriptions.findByDodoSubscriptionId(subscriptionId);
      if (owner.isPresent() && !owner.get().tenantId().equals(tenantId)) {
        log.warn("Billing webhook {} names subscription {} that belongs to another workspace; ignored", webhookId, subscriptionId);
        return;
      }
    }
    Instant eventTime = parseInstant(root.path("timestamp").asText(null));
    if (eventTime == null) {
      eventTime = now;
    }
    // While a subscription is live (active or in payment grace) a different id is a duplicate, not a replacement.
    // After it was cancelled, expired or lapsed, a new subscription takes over the workspace.
    boolean live = "ACTIVE".equals(sub.status()) || "ON_HOLD".equals(sub.status());
    if (live && sub.dodoSubscriptionId() != null && subscriptionId != null
        && !sub.dodoSubscriptionId().equals(subscriptionId)) {
      log.warn("Billing webhook {} carries subscription {} but workspace already has live subscription {}; ignored",
          webhookId, subscriptionId, sub.dodoSubscriptionId());
      return;
    }
    if (eventTime.isBefore(sub.updatedAt())) {
      return; // older than what we already hold
    }
    Instant periodEnd = parseInstant(data.path("next_billing_date").asText(null));
    switch (type) {
      case "subscription.active", "subscription.renewed", "subscription.plan_changed", "subscription.unpaused" ->
          sub.activate(pi.plan().name(), pi.interval(), textOrNull(data.path("customer").path("customer_id")),
              textOrNull(data.path("subscription_id")), periodEnd, eventTime);
      case "subscription.on_hold", "subscription.past_due" -> sub.hold(eventTime.plus(PAYMENT_GRACE), eventTime);
      case "subscription.cancelled" -> sub.cancel(periodEnd != null ? periodEnd : eventTime, eventTime);
      case "subscription.expired" -> sub.cancel(eventTime, eventTime);
      default -> {
        return;
      }
    }
    subscriptions.save(sub);
    users.findFirstByTenantIdAndRoleOrderByCreatedAtAsc(tenantId, UserRole.ADMIN).ifPresent(admin -> {
      tenantContext.setOverrides(tenantId, admin.id());
      try {
        audit.append(null, "billing." + type, "subscription", sub.dodoSubscriptionId() == null ? "" : sub.dodoSubscriptionId(),
            Map.of("plan", sub.planCode(), "status", sub.status()));
      } finally {
        tenantContext.clearOverrides();
      }
    });
  }

  private static String textOrNull(JsonNode node) {
    return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
  }

  private static Instant parseInstant(String text) {
    if (text == null || text.isBlank() || "null".equals(text)) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException e) {
      try {
        return java.time.OffsetDateTime.parse(text).toInstant();
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }

  private static String sha256Hex(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }
}
