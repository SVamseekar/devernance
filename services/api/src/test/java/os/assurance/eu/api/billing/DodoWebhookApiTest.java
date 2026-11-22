package os.assurance.eu.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import os.assurance.eu.api.email.EmailMessage;
import os.assurance.eu.api.email.EmailSender;
import os.assurance.eu.api.tenant.UserJpaRepository;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret",
    "assurance.app.base-url=https://app.test",
    "assurance.security.auth-rate.per-ip-per-15m=1000",
    "assurance.auth.email.cooldown-seconds=0",
    "assurance.dodo.webhook-secret=" + StandardWebhookVerifierTest.SECRET,
    "assurance.dodo.product-team-monthly=prod_team_m",
    "assurance.dodo.product-team-yearly=prod_team_y",
    "assurance.dodo.product-business-monthly=prod_bus_m",
    "assurance.dodo.product-business-yearly=prod_bus_y",
    "spring.jackson.mapper.accept-case-insensitive-enums=true"
})
@AutoConfigureMockMvc
class DodoWebhookApiTest {
  private static final String PASSWORD = "correct-horse-battery";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserJpaRepository users;
  @MockitoSpyBean EmailSender emailSender;

  record Workspace(String bearer, UUID tenantId) {}

  private Workspace newWorkspace() throws Exception {
    String email = "bill-" + UUID.randomUUID() + "@acme.example";
    mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"organisationName\":\"Bill " + UUID.randomUUID() + "\"}"))
        .andExpect(status().isAccepted());
    ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
    String body = captor.getAllValues().stream().filter(m -> email.equals(m.to()))
        .reduce((a, b) -> b).orElseThrow().textBody();
    String token = body.substring(body.indexOf("token=") + 6).split("\\s")[0];
    String verified = mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String bearer = "Bearer " + json.readTree(verified).get("accessToken").asText();
    UUID tenantId = users.findByEmailIgnoreCase(email).orElseThrow().tenantId();
    jdbc.update("update tenants set trial_ends_at = ? where id = ?",
        Timestamp.from(Instant.now().minusSeconds(60)), tenantId); // start from Free, so a paid plan is visible
    return new Workspace(bearer, tenantId);
  }

  private static String event(String type, String timestamp, UUID tenantId, String product, String nextBilling) {
    return """
        {"type":"%s","timestamp":"%s","data":{"subscription_id":"sub_%s","product_id":"%s",
         "customer":{"customer_id":"cus_%s"},"next_billing_date":"%s",
         "metadata":{"tenant_id":"%s"}}}""".formatted(type, timestamp, tenantId.toString().substring(0, 8), product,
        tenantId.toString().substring(0, 8), nextBilling, tenantId);
  }

  private ResultActions deliver(String id, String body, boolean validSignature) throws Exception {
    byte[] raw = body.getBytes(StandardCharsets.UTF_8);
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String signature = validSignature ? StandardWebhookVerifierTest.sign(id, ts, raw) : "v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    return mockMvc.perform(post("/api/v1/billing/webhooks/dodo").contentType(MediaType.APPLICATION_JSON)
        .header("webhook-id", id).header("webhook-timestamp", ts).header("webhook-signature", signature)
        .content(raw));
  }

  private String id() {
    return "msg_" + UUID.randomUUID();
  }

  private long eventRows(String webhookId) {
    return jdbc.queryForObject("select count(*) from billing_webhook_events where webhook_id = ?", Long.class, webhookId);
  }

  @Test
  void activeSubscriptionMakesTheWorkspaceTeam() throws Exception {
    Workspace w = newWorkspace();
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"));

    deliver(id(), event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan").value("TEAM"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.interval").value("MONTHLY"));
  }

  @Test
  void aRedeliveredWebhookChangesNothing() throws Exception {
    Workspace w = newWorkspace();
    String id = id();
    String body = event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_team_m", "2099-12-10T12:00:00Z");
    deliver(id, body, true).andExpect(status().isOk());
    jdbc.update("update tenant_subscriptions set plan_code = 'BUSINESS' where tenant_id = ?", w.tenantId());

    deliver(id, body, true).andExpect(status().isOk());

    assertThat(eventRows(id)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select plan_code from tenant_subscriptions where tenant_id = ?", String.class,
        w.tenantId())).isEqualTo("BUSINESS"); // the replay did not overwrite our marker
  }

  @Test
  void aBadSignatureIsRefusedAndRecordsNothing() throws Exception {
    Workspace w = newWorkspace();
    String id = id();
    deliver(id, event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), false).andExpect(status().isUnauthorized());
    assertThat(eventRows(id)).isZero();
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("FREE"));
  }

  @Test
  void aTamperedBodyIsRefused() throws Exception {
    Workspace w = newWorkspace();
    String id = id();
    String body = event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_team_m", "2099-12-10T12:00:00Z");
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String signature = StandardWebhookVerifierTest.sign(id, ts, body.getBytes(StandardCharsets.UTF_8));
    mockMvc.perform(post("/api/v1/billing/webhooks/dodo").contentType(MediaType.APPLICATION_JSON)
            .header("webhook-id", id).header("webhook-timestamp", ts).header("webhook-signature", signature)
            .content(body.replace("prod_team_m", "prod_bus_m")))
        .andExpect(status().isUnauthorized());
    assertThat(eventRows(id)).isZero();
  }

  @Test
  void aFailedPaymentKeepsThePlanForAWeekThenDropsToFree() throws Exception {
    Workspace w = newWorkspace();
    // The grace window is event time + 7 days, so the events must be recent or the grace has already ended.
    String activated = Instant.now().minusSeconds(2 * 3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
    String onHold = Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
    deliver(id(), event("subscription.active", activated, w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    deliver(id(), event("subscription.on_hold", onHold, w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("TEAM")).andExpect(jsonPath("$.status").value("ON_HOLD"))
        .andExpect(jsonPath("$.graceUntil").isNotEmpty());

    jdbc.update("update tenant_subscriptions set grace_until = ? where tenant_id = ?",
        Timestamp.from(Instant.now().minusSeconds(5)), w.tenantId());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("FREE"));
  }

  @Test
  void cancellationKeepsAccessUntilThePaidPeriodEnds() throws Exception {
    Workspace w = newWorkspace();
    deliver(id(), event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_bus_y",
        "2099-11-10T12:00:00Z"), true).andExpect(status().isOk());
    deliver(id(), event("subscription.cancelled", "2026-11-12T12:00:00Z", w.tenantId(), "prod_bus_y",
        "2099-11-10T12:00:00Z"), true).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("BUSINESS")).andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void aLateOlderEventNeverUndoesANewerOne() throws Exception {
    Workspace w = newWorkspace();
    deliver(id(), event("subscription.active", "2026-11-12T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    // an on_hold stamped BEFORE the activation arrives afterwards
    deliver(id(), event("subscription.on_hold", "2026-11-11T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void aRenewalWithAnEarlierPeriodNeverShortensTheCurrentOne() throws Exception {
    Workspace w = newWorkspace();
    deliver(id(), event("subscription.renewed", "2026-12-10T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-02-10T12:00:00Z"), true).andExpect(status().isOk());
    deliver(id(), event("subscription.active", "2026-12-11T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-01-10T12:00:00Z"), true).andExpect(status().isOk());
    Timestamp end = jdbc.queryForObject("select current_period_end from tenant_subscriptions where tenant_id = ?",
        Timestamp.class, w.tenantId());
    assertThat(end.toInstant()).isEqualTo(Instant.parse("2099-02-10T12:00:00Z"));
  }

  @Test
  void aWebhookForAnUnknownWorkspaceIsAcknowledgedAndIgnored() throws Exception {
    String id = id();
    deliver(id, event("subscription.active", "2026-11-10T12:00:00Z", UUID.randomUUID(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    assertThat(eventRows(id)).isEqualTo(1);
  }

  @Test
  void aProductWeDoNotSellChangesNothingAndCanBeResentOnceConfigured() throws Exception {
    Workspace w = newWorkspace();
    String eventId = id();
    deliver(eventId, event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_unknown",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("FREE"));
    // not marked processed: a "resend" from the Dodo dashboard after fixing the product ids must be able to apply it
    assertThat(eventRows(eventId)).isZero();
  }

  @Test
  void theActivationIsAuditedOnTheRightWorkspaceOnly() throws Exception {
    Workspace w = newWorkspace();
    Workspace other = newWorkspace();
    deliver(id(), event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where tenant_id = ? and event_type = 'billing.subscription.active'",
        Long.class, w.tenantId())).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where tenant_id = ? and event_type like 'billing.%'",
        Long.class, other.tenantId())).isZero();
  }

  @Test
  void aSubscriptionAlreadyBoundToOneWorkspaceCannotBeAppliedToAnother() throws Exception {
    Workspace a = newWorkspace();
    Workspace b = newWorkspace();
    deliver(id(), event("subscription.active", "2026-11-10T12:00:00Z", a.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    String subOfA = jdbc.queryForObject("select dodo_subscription_id from tenant_subscriptions where tenant_id = ?",
        String.class, a.tenantId());

    // a signed event that names workspace B but carries A's subscription id
    String crossed = event("subscription.active", "2026-11-11T12:00:00Z", b.tenantId(), "prod_bus_m",
        "2099-12-10T12:00:00Z").replace("\"subscription_id\":\"sub_" + b.tenantId().toString().substring(0, 8) + "\"",
        "\"subscription_id\":\"" + subOfA + "\"");
    deliver(id(), crossed, true).andExpect(status().isOk()); // acknowledged, so Dodo stops retrying

    mockMvc.perform(get("/api/v1/billing").header("Authorization", b.bearer()))
        .andExpect(jsonPath("$.plan").value("FREE"));
    mockMvc.perform(get("/api/v1/billing").header("Authorization", a.bearer()))
        .andExpect(jsonPath("$.plan").value("TEAM"));
  }

  @Test
  void aWorkspaceKeepsItsOwnSubscriptionWhenAnotherIdArrives() throws Exception {
    Workspace a = newWorkspace();
    deliver(id(), event("subscription.active", "2026-11-10T12:00:00Z", a.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    String other = event("subscription.plan_changed", "2026-11-11T12:00:00Z", a.tenantId(), "prod_bus_m",
        "2099-12-10T12:00:00Z").replace("\"subscription_id\":\"sub_" + a.tenantId().toString().substring(0, 8) + "\"",
        "\"subscription_id\":\"sub_someone_elses\"");
    deliver(id(), other, true).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", a.bearer()))
        .andExpect(jsonPath("$.plan").value("TEAM"));
  }

  @Test
  void aNewSubscriptionAfterCancellationLapsesIsAccepted() throws Exception {
    Workspace w = newWorkspace();
    String short8 = w.tenantId().toString().substring(0, 8);
    deliver(id(), event("subscription.active", "2026-09-10T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    deliver(id(), event("subscription.expired", "2026-09-20T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("FREE"));

    String resubscribed = event("subscription.active", "2026-10-01T12:00:00Z", w.tenantId(), "prod_bus_m",
        "2099-12-10T12:00:00Z").replace("\"subscription_id\":\"sub_" + short8 + "\"", "\"subscription_id\":\"sub_second\"");
    deliver(id(), resubscribed, true).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("BUSINESS"));
    assertThat(jdbc.queryForObject("select dodo_subscription_id from tenant_subscriptions where tenant_id = ?",
        String.class, w.tenantId())).isEqualTo("sub_second");
  }

  @Test
  void anActiveSubscriptionThatMissedRenewalLapsesAfterAWeek() throws Exception {
    Workspace w = newWorkspace();
    deliver(id(), event("subscription.active", "2026-11-10T12:00:00Z", w.tenantId(), "prod_team_m",
        "2099-12-10T12:00:00Z"), true).andExpect(status().isOk());
    jdbc.update("update tenant_subscriptions set current_period_end = ? where tenant_id = ?",
        Timestamp.from(Instant.now().minusSeconds(8L * 24 * 3600)), w.tenantId());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("FREE"));
    jdbc.update("update tenant_subscriptions set current_period_end = ? where tenant_id = ?",
        Timestamp.from(Instant.now().minusSeconds(2L * 24 * 3600)), w.tenantId());
    mockMvc.perform(get("/api/v1/billing").header("Authorization", w.bearer()))
        .andExpect(jsonPath("$.plan").value("TEAM"));
  }

  @Test
  void billingSummaryNeedsASignedInUser() throws Exception {
    mockMvc.perform(get("/api/v1/billing")).andExpect(status().isUnauthorized());
  }
}
