package os.assurance.eu.api.governance;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.governance.GovernanceViews.ApprovalView;
import os.assurance.eu.api.governance.GovernanceViews.DecisionView;
import os.assurance.eu.api.governance.GovernanceViews.ExecutionView;
import os.assurance.eu.api.governance.GovernanceViews.PolicyRef;
import os.assurance.eu.api.governance.GovernanceViews.TokenVerification;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

/**
 * The policy decision point. An enforcement point (the SDK wrapper around a tool, a gateway, the protected
 * service itself) asks "may this agent do this?" and gets ALLOW, DENY or REVIEW. Every answer is stored with
 * the policy and system version that produced it, and written to the audit chain.
 *
 * <p>Life of one request:
 * <ol>
 *   <li>{@link #authorize}: decide and record. REVIEW opens an approval request.</li>
 *   <li>{@link #decideApproval}: a person with one of the policy's approver roles approves or rejects.</li>
 *   <li>{@link #verify}: the protected service checks the signed authorization token before acting.</li>
 *   <li>{@link #recordExecution}: the caller reports that the action ran or failed. Once only.</li>
 * </ol>
 */
@Service
public class GovernanceAuthorizationService {
  static final String TOKEN_PURPOSE = "governance-authorization";
  static final int MAX_PAYLOAD_BYTES = 16 * 1024;
  static final int MAX_PAGE = 200;

  public record AuthorizeRequest(
      String system,
      String action,
      Map<String, Object> resource,
      Map<String, Object> context,
      String model,
      String environment,
      Boolean dryRun) {
  }

  public record ExecutionRequest(ExecutionStatus status, String detail, String reference) {
  }

  public record VerifyRequest(
      String token, String action, Map<String, Object> resource, Map<String, Object> context) {
  }

  public record DecisionQuery(
      String system, String action, DecisionStatus status, Instant from, Instant to, Integer limit) {
  }

  private final GovernanceDecisionJpaRepository decisions;
  private final GovernanceApprovalJpaRepository approvals;
  private final GovernanceResourceJpaRepository resources;
  private final GovernanceRegistry registry;
  private final GovernanceDriftService drift;
  private final TenantAuthorizationService authorization;
  private final TenantContext tenantContext;
  private final AuditService audit;
  private final JwtService jwt;
  private final Clock clock;
  private final Duration tokenTtl;

  public GovernanceAuthorizationService(
      GovernanceDecisionJpaRepository decisions,
      GovernanceApprovalJpaRepository approvals,
      GovernanceResourceJpaRepository resources,
      GovernanceRegistry registry,
      GovernanceDriftService drift,
      TenantAuthorizationService authorization,
      TenantContext tenantContext,
      AuditService audit,
      JwtService jwt,
      Clock clock,
      @Value("${assurance.governance.authorization-ttl-seconds:300}") long tokenTtlSeconds) {
    this.decisions = decisions;
    this.approvals = approvals;
    this.resources = resources;
    this.registry = registry;
    this.drift = drift;
    this.authorization = authorization;
    this.tenantContext = tenantContext;
    this.audit = audit;
    this.jwt = jwt;
    this.clock = clock;
    this.tokenTtl = Duration.ofSeconds(Math.max(30, tokenTtlSeconds));
  }

  @Transactional
  public DecisionView authorize(AuthorizeRequest request, GovernanceCaller caller) {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD);
    String action = request.action() == null ? "" : request.action().trim();
    if (!PolicySpec.isAction(action)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "action: must look like payment.refund (lowercase words joined by dots)");
    }
    Map<String, Object> resource = payload("resource", request.resource());
    Map<String, Object> context = payload("context", request.context());
    String model = request.model() == null || request.model().isBlank() ? null : request.model().trim();
    if (model != null && model.length() > 256) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "model: at most 256 characters");
    }
    String environment = GovernanceDriftService.environment(request.environment());
    GovernanceResourceEntity system = registry.requireSystem(request.system());
    GovernanceResourceEntity policyResource = registry.activePolicy(system.systemId()).orElse(null);

    PolicyEvaluator.Evaluation evaluation;
    boolean enforced = true;
    if (policyResource == null) {
      evaluation = PolicyEvaluator.Evaluation.of(DecisionStatus.DENY, "policy",
          "No GovernancePolicy targets this system, so nothing is allowed");
    } else {
      PolicySpec policy = GovernanceRegistry.policySpec(policyResource);
      enforced = policy.runtimeEnforced();
      if (policy.driftBlocks() && drift.hasDrift(system, environment)) {
        evaluation = PolicyEvaluator.Evaluation.of(DecisionStatus.DENY, "enforcement.onDrift",
            "Governance drift in " + environment + ": the running system differs from the approved state");
      } else {
        evaluation = PolicyEvaluator.evaluate(policy, new PolicyEvaluator.Request(action, resource, context, model));
      }
    }

    Instant now = clock.instant();
    boolean dryRun = Boolean.TRUE.equals(request.dryRun());
    GovernanceDecisionEntity decision = new GovernanceDecisionEntity(
        dryRun ? null : UUID.randomUUID(), tenantContext.tenantId(), system.systemId(), system, policyResource,
        caller.agent(), caller.apiKeyId(), tenantContext.actorId(), action, resource, context,
        requestSha256(action, resource, context), model, environment, evaluation.status(), enforced,
        evaluation.rule(), evaluation.reason(), now);
    if (dryRun) {
      return view(decision, null, system.name(), now, false);
    }
    decisions.save(decision);
    GovernanceApprovalEntity approval = null;
    if (evaluation.status() == DecisionStatus.REVIEW && enforced) {
      approval = approvals.save(new GovernanceApprovalEntity(
          UUID.randomUUID(), tenantContext.tenantId(), decision.id(), system.systemId(), evaluation.approverRoles(),
          evaluation.reason(), now, now.plus(Duration.ofMinutes(evaluation.expiresAfterMinutes()))));
    }
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("action", action);
    event.put("status", evaluation.status().name());
    event.put("rule", evaluation.rule());
    event.put("agent", caller.agent());
    event.put("enforced", enforced);
    event.put("policy", policyResource == null ? "" : policyResource.name() + "@" + policyResource.version());
    event.put("requestSha256", decision.requestSha256());
    audit.append(system.systemId(), "governance.decision", "governance_decision", decision.id().toString(), event);
    return view(decision, approval, system.name(), now, true);
  }

  /**
   * Reads one decision. The authorization token is a bearer credential for the protected service, so it is only
   * minted for the caller that made the request (the same API key, or the same person in a session). Anyone else
   * who may read the decision sees it without a token.
   */
  @Transactional(readOnly = true)
  public DecisionView decision(UUID id, GovernanceCaller caller) {
    GovernanceDecisionEntity decision = decisions.findByIdAndTenantId(id, tenantContext.tenantId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision not found"));
    GovernanceApprovalEntity approval =
        approvals.findByDecisionIdAndTenantId(id, tenantContext.tenantId()).orElse(null);
    return view(decision, approval, systemName(decision), clock.instant(), isRequester(decision, caller));
  }

  /** True when {@code caller} is who asked for {@code decision}: the same API key, or the same signed-in person. */
  private boolean isRequester(GovernanceDecisionEntity decision, GovernanceCaller caller) {
    if (decision.apiKeyId() != null) {
      return decision.apiKeyId().equals(caller.apiKeyId());
    }
    return !caller.isApiKey() && decision.actorId().equals(tenantContext.actorId());
  }

  /** Newest first. Filters are optional; {@code to} is exclusive. */
  @Transactional(readOnly = true)
  public List<DecisionView> decisions(DecisionQuery query) {
    UUID tenantId = tenantContext.tenantId();
    UUID systemId = query.system() == null || query.system().isBlank()
        ? null : registry.requireSystem(query.system()).systemId();
    int limit = query.limit() == null ? 50 : Math.max(1, Math.min(MAX_PAGE, query.limit()));
    Specification<GovernanceDecisionEntity> filter = (root, criteria, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(builder.equal(root.get("tenantId"), tenantId));
      if (systemId != null) {
        predicates.add(builder.equal(root.get("systemId"), systemId));
      }
      if (query.action() != null && !query.action().isBlank()) {
        predicates.add(builder.equal(root.get("action"), query.action().trim()));
      }
      if (query.status() != null) {
        predicates.add(builder.equal(root.get("status"), query.status()));
      }
      if (query.from() != null) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
      }
      if (query.to() != null) {
        predicates.add(builder.lessThan(root.get("createdAt"), query.to()));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
    List<GovernanceDecisionEntity> page = decisions.findAll(
        filter, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    return views(page);
  }

  /** Approval requests, newest first; only those with {@code status} when given. */
  @Transactional(readOnly = true)
  public List<ApprovalView> approvals(ApprovalStatus status, Integer limit) {
    UUID tenantId = tenantContext.tenantId();
    Limit max = Limit.of(limit == null ? 50 : Math.max(1, Math.min(MAX_PAGE, limit)));
    Instant now = clock.instant();
    // PENDING and APPROVED rows can have expired since they were written, so those are filtered after loading.
    List<GovernanceApprovalEntity> rows = status == null || status == ApprovalStatus.EXPIRED
        ? approvals.findByTenantIdOrderByRequestedAtDesc(tenantId, Limit.of(MAX_PAGE))
        : approvals.findByTenantIdAndStatusOrderByRequestedAtDesc(tenantId, status, Limit.of(MAX_PAGE));
    Map<UUID, GovernanceDecisionEntity> byId = new HashMap<>();
    decisions.findAllByTenantIdAndIdIn(tenantId, rows.stream().map(GovernanceApprovalEntity::decisionId).toList())
        .forEach(d -> byId.put(d.id(), d));
    Map<UUID, String> names = systemNames(byId.values().stream().toList());
    List<ApprovalView> out = new ArrayList<>();
    for (GovernanceApprovalEntity row : rows) {
      GovernanceDecisionEntity decision = byId.get(row.decisionId());
      if (decision == null) {
        continue;
      }
      ApprovalView view = approvalView(row, decision, names.get(decision.systemResourceId()), now);
      if (status == null || view.status() == status) {
        out.add(view);
      }
      if (out.size() >= max.max()) {
        break;
      }
    }
    return out;
  }

  /**
   * Approves or rejects. The caller must hold one of the roles the policy named for this review, and may not
   * answer a request they made: in a session, or through an API key they created (a key acts as its creator, so
   * {@code actorId} is the key's owner).
   */
  @Transactional
  public ApprovalView decideApproval(UUID approvalId, boolean approve, String rationale) {
    UUID tenantId = tenantContext.tenantId();
    GovernanceApprovalEntity approval = approvals.findForUpdate(approvalId, tenantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
    GovernanceDecisionEntity decision = decisions.findByIdAndTenantId(approval.decisionId(), tenantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision not found"));
    Instant now = clock.instant();
    ApprovalStatus current = approval.statusAt(now);
    if (current != ApprovalStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "This approval request is already " + current.name().toLowerCase(java.util.Locale.ROOT));
    }
    UserRole role = authorization.currentRole();
    if (!approval.approverRoles().contains(role.name())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Only " + String.join(" or ", approval.approverRoles()) + " may answer this approval request");
    }
    if (decision.actorId().equals(tenantContext.actorId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You cannot answer a request made by you or by an API key you created");
    }
    String note = rationale == null ? "" : rationale.trim();
    if (note.length() > 2048) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rationale: at most 2048 characters");
    }
    if (!approve && note.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rationale: say why the request is rejected");
    }
    approval.decide(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED, tenantContext.actorId(),
        note.isEmpty() ? null : note, now);
    approvals.save(approval);
    audit.append(decision.systemId(), approve ? "governance.approval_approved" : "governance.approval_rejected",
        "governance_approval", approval.id().toString(), Map.of(
            "decisionId", decision.id().toString(),
            "action", decision.action(),
            "role", role.name()));
    return approvalView(approval, decision, systemName(decision), now);
  }

  /**
   * Records, once, what happened to an authorized action. A denied or unapproved action cannot be reported run;
   * the attempt is refused, and the refusal itself is committed to the audit chain.
   */
  @Transactional(noRollbackFor = UnauthorizedExecutionException.class)
  public DecisionView recordExecution(UUID decisionId, ExecutionRequest request) {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD);
    if (request == null || request.status() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status: must be EXECUTED or FAILED");
    }
    String detail = optional("detail", request.detail(), 1024);
    String reference = optional("reference", request.reference(), 256);
    UUID tenantId = tenantContext.tenantId();
    GovernanceDecisionEntity decision = decisions.findForUpdate(decisionId, tenantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision not found"));
    if (decision.executionStatus() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "The outcome of this decision is already recorded");
    }
    GovernanceApprovalEntity approval = approvals.findByDecisionIdAndTenantId(decisionId, tenantId).orElse(null);
    Instant now = clock.instant();
    if (!authorized(decision, approval, now)) {
      // The attempt itself is evidence: something ran, or tried to, without authorization.
      audit.append(decision.systemId(), "governance.execution_refused", "governance_decision",
          decision.id().toString(), Map.of("action", decision.action(), "reported", request.status().name()));
      throw new UnauthorizedExecutionException();
    }
    decision.recordExecution(request.status(), detail, reference, now);
    decisions.save(decision);
    audit.append(decision.systemId(), "governance.execution_recorded", "governance_decision",
        decision.id().toString(), Map.of(
            "action", decision.action(),
            "status", request.status().name(),
            "reference", reference == null ? "" : reference));
    return view(decision, approval, systemName(decision), now, false);
  }

  /** Does not roll the transaction back, so the audit event about the refusal is kept. Answered with 409. */
  public static class UnauthorizedExecutionException extends RuntimeException {
    public UnauthorizedExecutionException() {
      super("This decision does not authorize the action, so no outcome can be recorded for it");
    }
  }

  /**
   * For the protected service: is this token a real, unexpired, unused authorization for exactly this request?
   * The token can also be checked offline against {@code /.well-known/jwks.json}; only this call knows whether
   * it was already used.
   */
  @Transactional(readOnly = true)
  public TokenVerification verify(VerifyRequest request) {
    if (request == null || request.token() == null || request.token().isBlank() || request.action() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token and action are required");
    }
    Optional<Map<String, Object>> verified = jwt.verifyEvidence(request.token().trim());
    if (verified.isEmpty()) {
      return invalid("The token signature is not valid");
    }
    Map<String, Object> claims = verified.get();
    if (!TOKEN_PURPOSE.equals(claims.get("purpose"))) {
      return invalid("This is not an authorization token");
    }
    UUID tenantId = tenantContext.tenantId();
    if (!tenantId.toString().equals(claims.get("tenantId"))) {
      return invalid("The token was not issued for this workspace");
    }
    UUID decisionId;
    Instant expiresAt;
    try {
      decisionId = UUID.fromString(String.valueOf(claims.get("decisionId")));
      expiresAt = Instant.ofEpochSecond(((Number) claims.get("exp")).longValue());
    } catch (RuntimeException malformed) {
      return invalid("The token is malformed");
    }
    String action = request.action().trim();
    Instant now = clock.instant();
    if (!now.isBefore(expiresAt)) {
      return new TokenVerification(false, "The token has expired", decisionId, action, expiresAt);
    }
    String sha = requestSha256(action, payload("resource", request.resource()), payload("context", request.context()));
    if (!sha.equals(claims.get("requestSha256"))) {
      return new TokenVerification(false, "The request differs from the one that was authorized", decisionId,
          action, expiresAt);
    }
    GovernanceDecisionEntity decision = decisions.findByIdAndTenantId(decisionId, tenantId).orElse(null);
    if (decision == null) {
      return new TokenVerification(false, "The decision no longer exists", decisionId, action, expiresAt);
    }
    if (decision.executionStatus() != null) {
      return new TokenVerification(false, "The authorization was already used", decisionId, action, expiresAt);
    }
    GovernanceApprovalEntity approval = approvals.findByDecisionIdAndTenantId(decisionId, tenantId).orElse(null);
    if (!authorized(decision, approval, now)) {
      return new TokenVerification(false, "The decision no longer authorizes the action", decisionId, action,
          expiresAt);
    }
    return new TokenVerification(true, "Authorized", decisionId, action, expiresAt);
  }

  private static TokenVerification invalid(String reason) {
    return new TokenVerification(false, reason, null, null, null);
  }

  /**
   * SHA-256 of what was asked: the action, the resource and the context. Numbers are compared by value, so
   * 75000 and 75000.0 give the same hash and a service that re-serializes the request still matches.
   */
  static String requestSha256(String action, Map<String, Object> resource, Map<String, Object> context) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("action", action);
    request.put("context", normalize(context));
    request.put("resource", normalize(resource));
    return GovernanceJson.sha256Hex(GovernanceJson.canonical(request));
  }

  private static Object normalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new TreeMap<>();
      map.forEach((key, item) -> out.put(String.valueOf(key), normalize(item)));
      return out;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(GovernanceAuthorizationService::normalize).toList();
    }
    if (value instanceof Number number) {
      return "number:" + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    }
    return value;
  }

  /** May the action run now? The single rule every caller of this service shares. */
  static boolean authorized(GovernanceDecisionEntity decision, GovernanceApprovalEntity approval, Instant now) {
    if (!decision.enforced()) {
      return true;
    }
    return switch (decision.status()) {
      case ALLOW -> true;
      case DENY -> false;
      case REVIEW -> approval != null
          && (decision.executionStatus() != null
              ? approval.status() == ApprovalStatus.APPROVED
              : approval.statusAt(now) == ApprovalStatus.APPROVED);
    };
  }

  private List<DecisionView> views(List<GovernanceDecisionEntity> page) {
    UUID tenantId = tenantContext.tenantId();
    Map<UUID, GovernanceApprovalEntity> approvalByDecision = new HashMap<>();
    approvals.findAllByTenantIdAndDecisionIdIn(tenantId, page.stream().map(GovernanceDecisionEntity::id).toList())
        .forEach(a -> approvalByDecision.put(a.decisionId(), a));
    Map<UUID, String> names = systemNames(page);
    Instant now = clock.instant();
    return page.stream()
        .map(d -> view(d, approvalByDecision.get(d.id()), names.get(d.systemResourceId()), now, false))
        .toList();
  }

  private Map<UUID, String> systemNames(List<GovernanceDecisionEntity> page) {
    Map<UUID, String> names = new HashMap<>();
    List<UUID> ids = page.stream().map(GovernanceDecisionEntity::systemResourceId).distinct().toList();
    resources.findAllByTenantIdAndIdIn(tenantContext.tenantId(), ids).forEach(r -> names.put(r.id(), r.name()));
    return names;
  }

  private String systemName(GovernanceDecisionEntity decision) {
    return resources.findById(decision.systemResourceId())
        .filter(r -> r.tenantId().equals(tenantContext.tenantId()))
        .map(GovernanceResourceEntity::name).orElse("");
  }

  /** {@code withToken}: single reads mint the signed token; lists do not (a list is for reading, not acting). */
  private DecisionView view(
      GovernanceDecisionEntity d, GovernanceApprovalEntity approval, String systemName, Instant now,
      boolean withToken) {
    boolean authorized = authorized(d, approval, now);
    String token = null;
    Instant tokenExpiry = null;
    if (withToken && authorized && d.id() != null && d.executionStatus() == null) {
      Instant expiry = approval != null
          ? min(now.plus(tokenTtl), approval.expiresAt())
          : d.createdAt().plus(tokenTtl);
      if (now.isBefore(expiry)) {
        tokenExpiry = Instant.ofEpochSecond(expiry.getEpochSecond());
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("purpose", TOKEN_PURPOSE);
        claims.put("decisionId", d.id().toString());
        claims.put("tenantId", d.tenantId().toString());
        claims.put("systemId", d.systemId().toString());
        claims.put("action", d.action());
        claims.put("requestSha256", d.requestSha256());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", tokenExpiry.getEpochSecond());
        token = jwt.signEvidence(claims);
      }
    }
    return new DecisionView(
        d.id(), systemName, d.systemId(), d.systemVersion(), d.action(), d.resource(), d.context(), d.model(),
        d.environment(), d.agent(), d.status(), d.enforced(), authorized, d.rule(), d.reason(),
        d.policyName() == null ? null : new PolicyRef(d.policyName(), d.policyVersion()),
        approval == null ? null : approvalView(approval, d, systemName, now),
        d.executionStatus() == null ? null
            : new ExecutionView(d.executionStatus(), d.executionDetail(), d.executionReference(),
                d.executionRecordedAt()),
        token, tokenExpiry, d.createdAt());
  }

  private static ApprovalView approvalView(
      GovernanceApprovalEntity a, GovernanceDecisionEntity d, String systemName, Instant now) {
    // Once the action has run, the approval is history: show what was decided, not that it has since lapsed.
    ApprovalStatus status = d.executionStatus() != null ? a.status() : a.statusAt(now);
    return new ApprovalView(a.id(), a.decisionId(), a.systemId(), systemName, d.action(), d.agent(), d.resource(),
        d.context(), status, a.approverRoles(), a.reason(), a.decidedBy(), a.rationale(), a.requestedAt(),
        a.expiresAt(), a.decidedAt());
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  private static Map<String, Object> payload(String field, Map<String, Object> value) {
    Map<String, Object> map = value == null ? Map.of() : value;
    if (GovernanceJson.utf8Length(GovernanceJson.canonical(map)) > MAX_PAYLOAD_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          field + ": at most " + MAX_PAYLOAD_BYTES / 1024 + " KB of JSON");
    }
    return map;
  }

  private static String optional(String field, String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (value.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + ": at most " + max + " characters");
    }
    return value.trim();
  }
}
