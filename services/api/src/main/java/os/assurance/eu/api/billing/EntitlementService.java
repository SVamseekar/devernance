package os.assurance.eu.api.billing;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import os.assurance.eu.api.system.AiSystemJpaRepository;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserInviteJpaRepository;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;

/**
 * Answers "which plan applies to this workspace right now" and enforces that plan's limits with HTTP 402. The plan is
 * computed on every read from the subscription, the trial end and the grace period, so a trial that ends or a grace
 * period that runs out needs no scheduled job.
 */
@Service
public class EntitlementService {
  /** Roles that count against the editor-seat limit. Auditors and legal counsel are viewers and are free. */
  static final Set<UserRole> EDITOR_ROLES =
      Set.of(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);

  /** How late a renewal webhook may be before an ACTIVE subscription is treated as lapsed. */
  static final java.time.Duration RENEWAL_SLACK = java.time.Duration.ofDays(7);

  public record Usage(int systems, int editors, int gateRunsThisMonth) {}

  private final TenantJpaRepository tenants;
  private final TenantSubscriptionJpaRepository subscriptions;
  private final AiSystemJpaRepository systemsJpa;
  private final UserJpaRepository users;
  private final UserInviteJpaRepository invites;
  private final GateRunCounterJpaRepository counters;
  private final TenantContext tenantContext;
  private final Clock clock;
  private final TransactionTemplate requiresNew;

  public EntitlementService(TenantJpaRepository tenants, TenantSubscriptionJpaRepository subscriptions,
      AiSystemJpaRepository systemsJpa, UserJpaRepository users, UserInviteJpaRepository invites,
      GateRunCounterJpaRepository counters, TenantContext tenantContext, Clock clock,
      PlatformTransactionManager transactions) {
    this.tenants = tenants;
    this.subscriptions = subscriptions;
    this.systemsJpa = systemsJpa;
    this.users = users;
    this.invites = invites;
    this.counters = counters;
    this.tenantContext = tenantContext;
    this.clock = clock;
    this.requiresNew = new TransactionTemplate(transactions);
    this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  static PlanCatalog resolve(String tenantPlanCode, Instant trialEndsAt, String subscriptionPlan,
      String subscriptionStatus, Instant graceOrPeriodEnd, Instant now) {
    if (subscriptionPlan != null && subscriptionStatus != null) {
      switch (subscriptionStatus) {
        case "ACTIVE", "TRIALING" -> {
          // A paid period that ended more than a week ago without a renewal webhook has lapsed.
          if (graceOrPeriodEnd == null || now.isBefore(graceOrPeriodEnd.plus(RENEWAL_SLACK))) {
            return PlanCatalog.valueOf(subscriptionPlan);
          }
        }
        case "ON_HOLD", "PAST_DUE", "CANCELLED" -> {
          if (graceOrPeriodEnd != null && now.isBefore(graceOrPeriodEnd)) {
            return PlanCatalog.valueOf(subscriptionPlan);
          }
        }
        default -> { }
      }
    }
    PlanCatalog base = PlanCatalog.fromCode(tenantPlanCode);
    if (base == PlanCatalog.TRIAL) {
      return trialEndsAt != null && now.isBefore(trialEndsAt) ? PlanCatalog.TRIAL : PlanCatalog.FREE;
    }
    return base;
  }

  @Transactional(readOnly = true)
  public PlanCatalog effectivePlan(UUID tenantId) {
    TenantEntity tenant = tenants.findById(tenantId).orElseThrow();
    var sub = subscriptions.findById(tenantId).orElse(null);
    Instant graceOrEnd = sub == null ? null : (sub.graceUntil() != null ? sub.graceUntil() : sub.currentPeriodEnd());
    return resolve(tenant.plan(), tenant.trialEndsAt(),
        sub == null ? null : sub.planCode(), sub == null ? null : sub.status(), graceOrEnd, clock.instant());
  }

  /** True when the current tenant's effective plan includes the feature. */
  public boolean hasFeature(Feature feature) {
    return effectivePlan(tenantContext.tenantId()).features().contains(feature);
  }

  public void requireFeature(Feature feature) {
    PlanCatalog plan = effectivePlan(tenantContext.tenantId());
    if (!plan.features().contains(feature)) {
      throw new PaymentRequiredException("feature_" + feature.name().toLowerCase(),
          feature + " is not included in the " + plan + " plan.");
    }
  }

  public void requireCanCreateSystem() {
    PlanCatalog plan = effectivePlan(tenantContext.tenantId());
    long count = systemsJpa.countByTenantId(tenantContext.tenantId());
    if (plan.gatedSystems() >= 0 && count >= plan.gatedSystems()) {
      throw new PaymentRequiredException("gated_systems",
          "Your " + plan + " plan includes " + plan.gatedSystems() + " gated system(s). Upgrade to add more.");
    }
  }

  /** Systems beyond the plan's limit (oldest kept) are read-only; reads and exports keep working. */
  public void requireSystemWritable(UUID systemId) {
    PlanCatalog plan = effectivePlan(tenantContext.tenantId());
    if (plan.gatedSystems() < 0) {
      return;
    }
    List<UUID> ordered = systemsJpa.findIdsByTenantIdOrderByCreatedAtAsc(tenantContext.tenantId());
    int index = ordered.indexOf(systemId);
    if (index >= plan.gatedSystems()) {
      throw new PaymentRequiredException("system_read_only",
          "This system is read-only on the " + plan + " plan. Upgrade to edit it.");
    }
  }

  /** Counts current editors plus editor invites still open, so invitations cannot exceed the limit either. */
  public void requireEditorSeatAvailable() {
    PlanCatalog plan = effectivePlan(tenantContext.tenantId());
    if (plan.editorSeats() < 0) {
      return;
    }
    if (editorsIncludingOpenInvites(tenantContext.tenantId()) >= plan.editorSeats()) {
      throw new PaymentRequiredException("editor_seats",
          "Your " + plan + " plan includes " + plan.editorSeats()
              + " editors. Invite viewers (auditor, legal) for free, or upgrade.");
    }
  }

  @Transactional
  public void recordGateRun() {
    UUID tenantId = tenantContext.tenantId();
    PlanCatalog plan = effectivePlan(tenantId);
    String period = currentPeriod();
    ensureCounter(tenantId, period);
    GateRunCounterEntity counter = counters.findForUpdate(tenantId, period)
        .orElseThrow(() -> new IllegalStateException("Gate run counter missing for " + tenantId + " " + period));
    if (plan.gateRunsPerMonth() >= 0 && counter.runs() >= plan.gateRunsPerMonth()) {
      throw new PaymentRequiredException("gate_runs",
          "Your " + plan + " plan includes " + plan.gateRunsPerMonth() + " CI gate runs per month.");
    }
    counter.increment();
    counters.save(counter);
  }

  /**
   * Creates this month's counter in its own transaction. Parallel pipelines (a CI matrix) often start the month
   * together; the loser of the insert race just uses the row the winner created instead of failing its build.
   */
  private void ensureCounter(UUID tenantId, String period) {
    // existsById, not findById: a loaded entity would be served stale to the locked read that follows.
    if (counters.existsById(new GateRunCounterEntity.Key(tenantId, period))) {
      return;
    }
    try {
      requiresNew.executeWithoutResult(status -> counters.saveAndFlush(new GateRunCounterEntity(tenantId, period, 0)));
    } catch (DataIntegrityViolationException raced) {
      // another request created it first
    }
  }

  @Transactional(readOnly = true)
  public Usage usage(UUID tenantId) {
    return new Usage(
        (int) systemsJpa.countByTenantId(tenantId),
        (int) editorsIncludingOpenInvites(tenantId),
        counters.runsIn(tenantId, currentPeriod()));
  }

  /** True while a paid subscription is live and still billing (active or in payment grace), so a second one must not start. */
  @Transactional(readOnly = true)
  public boolean hasBillingSubscription(UUID tenantId) {
    var sub = subscriptions.findById(tenantId).orElse(null);
    if (sub == null) {
      return false;
    }
    Instant now = clock.instant();
    return switch (sub.status()) {
      case "ACTIVE", "TRIALING" -> sub.currentPeriodEnd() == null || now.isBefore(sub.currentPeriodEnd().plus(RENEWAL_SLACK));
      case "ON_HOLD", "PAST_DUE" -> sub.graceUntil() != null && now.isBefore(sub.graceUntil());
      default -> false;
    };
  }

  private long editorsIncludingOpenInvites(UUID tenantId) {
    Instant now = clock.instant();
    long members = users.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
        .filter(u -> EDITOR_ROLES.contains(u.role())).count();
    long open = invites.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .filter(i -> i.acceptedAt() == null && i.expiresAt().isAfter(now) && EDITOR_ROLES.contains(i.role()))
        .count();
    return members + open;
  }

  private String currentPeriod() {
    return YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).toString();
  }
}
