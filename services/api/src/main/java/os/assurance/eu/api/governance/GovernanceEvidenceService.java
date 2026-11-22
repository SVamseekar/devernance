package os.assurance.eu.api.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

/**
 * What can be proven about a period of runtime governance.
 *
 * <p>{@link #bundle} exports the records (decisions, approvals, execution outcomes, the exact policy and system
 * versions they refer to) as files with a signed manifest, for an auditor to verify without access to
 * Devernance. {@link #closure} reports, per policy rule, whether the chain requirement, enforcement, observation
 * and verification is complete, and says plainly where it is not.
 *
 * <p>Neither can show an action that never asked for authorization. The bundle says so in its manifest.
 */
@Service
public class GovernanceEvidenceService {
  static final int MAX_DECISIONS = 5000;
  static final String BUNDLE_PURPOSE = "governance-evidence-bundle";
  static final String LIMITATION = "This bundle proves that these records were issued by this Devernance workspace "
      + "and have not been changed since export. It does not prove that every action in the real world asked "
      + "Devernance for authorization: that depends on enforcement being placed where the action is carried out.";

  public record RuleClosure(
      String rule,
      String requirement,
      String enforcement,
      long decisions,
      long allowed,
      long denied,
      long reviewed,
      long executed,
      long failed,
      /** Authorized actions whose outcome nobody reported. */
      long unreported,
      /** CLOSED when nothing is missing, otherwise OPEN. */
      String status,
      List<String> gaps) {
  }

  public record ClosureReport(
      String system,
      UUID systemId,
      Instant from,
      Instant to,
      GovernanceViews.PolicyRef policy,
      boolean auditChainValid,
      /** CLOSED only when every rule is CLOSED and the audit chain verifies. */
      String status,
      List<RuleClosure> rules,
      List<String> gaps,
      String limitation) {
  }

  private final GovernanceDecisionJpaRepository decisions;
  private final GovernanceApprovalJpaRepository approvals;
  private final GovernanceResourceJpaRepository resources;
  private final GovernanceObservationJpaRepository observations;
  private final GovernanceRegistry registry;
  private final TenantAuthorizationService authorization;
  private final TenantContext tenantContext;
  private final AuditService audit;
  private final JwtService jwt;
  private final Clock clock;
  private final String generator;

  public GovernanceEvidenceService(
      GovernanceDecisionJpaRepository decisions,
      GovernanceApprovalJpaRepository approvals,
      GovernanceResourceJpaRepository resources,
      GovernanceObservationJpaRepository observations,
      GovernanceRegistry registry,
      TenantAuthorizationService authorization,
      TenantContext tenantContext,
      AuditService audit,
      JwtService jwt,
      Clock clock,
      @Value("${assurance.evidence-pack.generator:devernance-api}") String generator) {
    this.decisions = decisions;
    this.approvals = approvals;
    this.resources = resources;
    this.observations = observations;
    this.registry = registry;
    this.authorization = authorization;
    this.tenantContext = tenantContext;
    this.audit = audit;
    this.jwt = jwt;
    this.clock = clock;
    this.generator = generator;
  }

  /** The decisions of one system in {@code [from, to)}, oldest first, with their approvals. */
  private record Period(
      GovernanceResourceEntity system,
      Instant from,
      Instant to,
      List<GovernanceDecisionEntity> decisions,
      Map<UUID, GovernanceApprovalEntity> approvalByDecision) {
  }

  private Period load(String systemNameOrId, Instant from, Instant to) {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER,
        UserRole.LEGAL_COUNSEL, UserRole.AUDITOR);
    Instant end = to == null ? clock.instant() : to;
    Instant start = from == null ? Instant.EPOCH : from;
    if (!start.isBefore(end)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    }
    GovernanceResourceEntity system = registry.requireSystem(systemNameOrId);
    UUID tenantId = tenantContext.tenantId();
    List<GovernanceDecisionEntity> rows =
        decisions.findByTenantIdAndSystemIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            tenantId, system.systemId(), start, end, Limit.of(MAX_DECISIONS + 1));
    if (rows.size() > MAX_DECISIONS) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "More than " + MAX_DECISIONS + " decisions in this period. Narrow from/to and export in parts.");
    }
    Map<UUID, GovernanceApprovalEntity> approvalByDecision = new HashMap<>();
    List<UUID> ids = rows.stream().map(GovernanceDecisionEntity::id).toList();
    for (int i = 0; i < ids.size(); i += 500) {
      approvals.findAllByTenantIdAndDecisionIdIn(tenantId, ids.subList(i, Math.min(ids.size(), i + 500)))
          .forEach(a -> approvalByDecision.put(a.decisionId(), a));
    }
    return new Period(system, start, end, rows, approvalByDecision);
  }

  @Transactional(readOnly = true)
  public ClosureReport closure(String systemNameOrId, Instant from, Instant to) {
    Period period = load(systemNameOrId, from, to);
    Optional<GovernanceResourceEntity> policyResource = registry.activePolicy(period.system().systemId());
    boolean chainValid = audit.recentVerification().valid();
    List<String> gaps = new ArrayList<>();
    if (!chainValid) {
      gaps.add("The audit chain does not verify. Records may have been altered; run the audit verify endpoint.");
    }
    List<RuleClosure> rules = new ArrayList<>();
    if (policyResource.isEmpty()) {
      gaps.add("No GovernancePolicy targets this system, so there is no requirement to evidence.");
    } else {
      PolicySpec policy = GovernanceRegistry.policySpec(policyResource.get());
      Instant now = clock.instant();
      for (PolicySpec.Rule rule : policy.rules()) {
        rules.add(closeRule(rule.action(), requirementOf(rule), policy, period, now));
      }
      rules.add(closeRule(PolicyEvaluator.DEFAULT_RULE,
          "Every action no rule covers is " + (policy.defaultDecision() == DecisionStatus.DENY ? "denied" : "allowed"),
          policy, period, now));
    }
    boolean closed = gaps.isEmpty() && rules.stream().allMatch(r -> r.status().equals("CLOSED"));
    return new ClosureReport(period.system().name(), period.system().systemId(), period.from(), period.to(),
        policyResource.map(p -> new GovernanceViews.PolicyRef(p.name(), p.version())).orElse(null), chainValid,
        closed ? "CLOSED" : "OPEN", rules, gaps, LIMITATION);
  }

  private static String requirementOf(PolicySpec.Rule rule) {
    if (rule.requirement() != null) {
      return rule.requirement();
    }
    if (rule.decision() == DecisionStatus.DENY) {
      return rule.action() + " is denied";
    }
    List<String> parts = new ArrayList<>();
    parts.add(rule.action() + " is allowed");
    if (!rule.require().isEmpty()) {
      parts.add("only when " + String.join(" and ",
          rule.require().stream().map(PolicySpec.Condition::describe).toList()));
    }
    if (!rule.review().isEmpty()) {
      parts.add("with human approval when its review conditions match");
    }
    return String.join(", ", parts);
  }

  private static RuleClosure closeRule(
      String ruleName, String requirement, PolicySpec policy, Period period, Instant now) {
    long total = 0;
    long allowed = 0;
    long denied = 0;
    long reviewed = 0;
    long executed = 0;
    long failed = 0;
    long unreported = 0;
    for (GovernanceDecisionEntity decision : period.decisions()) {
      if (!decision.rule().equals(ruleName)) {
        continue;
      }
      total++;
      switch (decision.status()) {
        case ALLOW -> allowed++;
        case DENY -> denied++;
        case REVIEW -> reviewed++;
      }
      if (decision.executionStatus() == ExecutionStatus.EXECUTED) {
        executed++;
      } else if (decision.executionStatus() == ExecutionStatus.FAILED) {
        failed++;
      } else if (decision.status() != DecisionStatus.DENY && GovernanceAuthorizationService.authorized(
          decision, period.approvalByDecision().get(decision.id()), now)) {
        unreported++;
      }
    }
    List<String> gaps = new ArrayList<>();
    if (!policy.runtimeEnforced()) {
      gaps.add("The policy runs in monitor mode: decisions are recorded but nothing is blocked.");
    }
    if (total == 0) {
      gaps.add("No decision used this rule in the period, so its enforcement is not demonstrated.");
    }
    if (unreported > 0) {
      gaps.add(unreported + " authorized action" + (unreported == 1 ? " has" : "s have")
          + " no reported outcome, so it is unknown whether " + (unreported == 1 ? "it" : "they") + " ran.");
    }
    return new RuleClosure(ruleName, requirement, policy.runtimeEnforced() ? "enforce" : "monitor", total, allowed,
        denied, reviewed, executed, failed, unreported, gaps.isEmpty() ? "CLOSED" : "OPEN", gaps);
  }

  /**
   * The evidence bundle as text files plus a signed manifest. Each file's content is returned exactly as it was
   * hashed, so a verifier hashes the bytes it received and never has to re-serialize JSON.
   *
   * <p>To verify: SHA-256 of each file equals its entry in the manifest; SHA-256 of the manifest equals
   * {@code manifestSha256}; the signature (JWS, RS256) verifies against {@code /.well-known/jwks.json} and
   * carries the same {@code manifestSha256}. `devernance evidence verify` does exactly this.
   */
  @Transactional
  public Map<String, Object> bundle(String systemNameOrId, Instant from, Instant to) {
    Period period = load(systemNameOrId, from, to);
    UUID tenantId = tenantContext.tenantId();
    GovernanceResourceEntity system = period.system();
    Instant generatedAt = clock.instant();
    Map<String, String> files = new TreeMap<>();

    // Every system and policy version a decision refers to, plus what is active now.
    Set<UUID> resourceIds = new LinkedHashSet<>();
    resourceIds.add(system.id());
    registry.activePolicy(system.systemId()).ifPresent(p -> resourceIds.add(p.id()));
    for (GovernanceDecisionEntity decision : period.decisions()) {
      resourceIds.add(decision.systemResourceId());
      if (decision.policyResourceId() != null) {
        resourceIds.add(decision.policyResourceId());
      }
    }
    Map<UUID, GovernanceResourceEntity> referenced = new HashMap<>();
    resources.findAllByTenantIdAndIdIn(tenantId, new ArrayList<>(resourceIds))
        .forEach(r -> referenced.put(r.id(), r));
    for (GovernanceResourceEntity resource : referenced.values()) {
      String folder = resource.kind() == GovernanceResourceKind.AI_SYSTEM ? "systems/" : "policies/";
      files.put(folder + resource.name() + "-v" + resource.version() + ".json",
          GovernanceJson.canonical(resourceRecord(resource)));
    }

    long allowed = 0;
    long denied = 0;
    long reviewed = 0;
    long executions = 0;
    long approvalCount = 0;
    for (GovernanceDecisionEntity decision : period.decisions()) {
      switch (decision.status()) {
        case ALLOW -> allowed++;
        case DENY -> denied++;
        case REVIEW -> reviewed++;
      }
      files.put("decisions/" + decision.id() + ".json",
          GovernanceJson.canonical(decisionRecord(decision, referenced)));
      GovernanceApprovalEntity approval = period.approvalByDecision().get(decision.id());
      if (approval != null) {
        approvalCount++;
        files.put("approvals/" + decision.id() + ".json", GovernanceJson.canonical(approvalRecord(approval)));
      }
      if (decision.executionStatus() != null) {
        executions++;
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("decisionId", decision.id().toString());
        execution.put("status", decision.executionStatus().name());
        execution.put("detail", decision.executionDetail());
        execution.put("reference", decision.executionReference());
        execution.put("recordedAt", decision.executionRecordedAt());
        files.put("executions/" + decision.id() + ".json", GovernanceJson.canonical(execution));
      }
    }

    List<Map<String, Object>> observed = new ArrayList<>();
    observations.findByTenantIdAndSystemIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            tenantId, system.systemId(), period.from(), period.to(), Limit.of(MAX_DECISIONS))
        .forEach(o -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("id", o.id().toString());
          row.put("environment", o.environment());
          row.put("observed", o.observed());
          row.put("inSyncWhenReported", o.inSync());
          row.put("source", o.source());
          row.put("reportedBy", o.reportedBy().toString());
          row.put("reportedAt", o.createdAt());
          observed.add(row);
        });
    files.put("drift/observations.json", GovernanceJson.canonical(observed));

    List<Map<String, Object>> index = new ArrayList<>();
    files.forEach((path, content) -> {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", path);
      entry.put("sha256", GovernanceJson.sha256Hex(content));
      entry.put("bytes", GovernanceJson.utf8Length(content));
      index.add(entry);
    });
    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("decisions", period.decisions().size());
    counts.put("allow", allowed);
    counts.put("deny", denied);
    counts.put("review", reviewed);
    counts.put("approvals", approvalCount);
    counts.put("executions", executions);
    counts.put("observations", observed.size());
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("bundleVersion", "1");
    manifest.put("generator", generator);
    manifest.put("generatedAt", generatedAt);
    manifest.put("system", Map.of("name", system.name(), "id", system.systemId().toString()));
    manifest.put("period", Map.of("from", period.from(), "to", period.to()));
    manifest.put("counts", counts);
    manifest.put("auditChainHead", audit.chainHeadHash().orElse(""));
    manifest.put("files", index);
    manifest.put("limitation", LIMITATION);
    String manifestText = GovernanceJson.canonical(manifest);
    String manifestSha = GovernanceJson.sha256Hex(manifestText);

    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("purpose", BUNDLE_PURPOSE);
    claims.put("manifestSha256", manifestSha);
    claims.put("systemId", system.systemId().toString());
    claims.put("generatedAt", generatedAt.toString());
    String signature = jwt.signEvidence(claims);

    audit.append(system.systemId(), "governance.evidence_bundle_exported", "ai_system",
        system.systemId().toString(), Map.of(
            "manifestSha256", manifestSha,
            "decisions", period.decisions().size(),
            "from", period.from().toString(),
            "to", period.to().toString()));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("manifest", manifestText);
    response.put("manifestSha256", manifestSha);
    response.put("signature", signature);
    response.put("jwksPath", "/.well-known/jwks.json");
    response.put("files", files);
    return response;
  }

  private static Map<String, Object> resourceRecord(GovernanceResourceEntity resource) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", resource.id().toString());
    record.put("kind", resource.kind().manifestKind());
    record.put("name", resource.name());
    record.put("version", resource.version());
    record.put("contentSha256", resource.contentSha256());
    record.put("spec", resource.spec());
    record.put("appliedBy", resource.createdBy().toString());
    record.put("appliedAt", resource.createdAt());
    return record;
  }

  private static Map<String, Object> decisionRecord(
      GovernanceDecisionEntity decision, Map<UUID, GovernanceResourceEntity> referenced) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", decision.id().toString());
    record.put("createdAt", decision.createdAt());
    record.put("agent", decision.agent());
    record.put("apiKeyId", decision.apiKeyId() == null ? null : decision.apiKeyId().toString());
    record.put("actorId", decision.actorId().toString());
    record.put("action", decision.action());
    record.put("resource", decision.resource());
    record.put("context", decision.context());
    record.put("requestSha256", decision.requestSha256());
    record.put("model", decision.model());
    record.put("environment", decision.environment());
    record.put("status", decision.status().name());
    record.put("enforced", decision.enforced());
    record.put("rule", decision.rule());
    record.put("reason", decision.reason());
    GovernanceResourceEntity system = referenced.get(decision.systemResourceId());
    Map<String, Object> systemRef = new LinkedHashMap<>();
    systemRef.put("name", system == null ? null : system.name());
    systemRef.put("version", decision.systemVersion());
    systemRef.put("contentSha256", system == null ? null : system.contentSha256());
    record.put("system", systemRef);
    if (decision.policyResourceId() != null) {
      GovernanceResourceEntity policy = referenced.get(decision.policyResourceId());
      Map<String, Object> policyRef = new LinkedHashMap<>();
      policyRef.put("name", decision.policyName());
      policyRef.put("version", decision.policyVersion());
      policyRef.put("contentSha256", policy == null ? null : policy.contentSha256());
      record.put("policy", policyRef);
    } else {
      record.put("policy", null);
    }
    return record;
  }

  private static Map<String, Object> approvalRecord(GovernanceApprovalEntity approval) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", approval.id().toString());
    record.put("decisionId", approval.decisionId().toString());
    record.put("status", approval.status().name());
    record.put("approverRoles", approval.approverRoles());
    record.put("reason", approval.reason());
    record.put("decidedBy", approval.decidedBy() == null ? null : approval.decidedBy().toString());
    record.put("rationale", approval.rationale());
    record.put("requestedAt", approval.requestedAt());
    record.put("expiresAt", approval.expiresAt());
    record.put("decidedAt", approval.decidedAt());
    return record;
  }
}
