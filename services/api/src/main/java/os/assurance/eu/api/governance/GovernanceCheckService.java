package os.assurance.eu.api.governance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.governance.GovernanceDriftService.DriftReport;
import os.assurance.eu.api.governance.GovernanceViews.PolicyRef;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.CiReleaseGateResponse;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateResponse;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.tenant.TenantContext;

/**
 * The checks a developer or a pipeline runs before anything is deployed:
 * <ul>
 *   <li>{@link #validate}: do the manifests (applied, or sent with the request) fit together?</li>
 *   <li>{@link #simulate}: what would the policy answer for these example requests? (Policy unit tests.)</li>
 *   <li>{@link #releaseCheck}: may this system be released? Combines validation, drift and the release gate.</li>
 *   <li>{@link #status} and {@link #graph}: what is governed, and how the pieces connect.</li>
 * </ul>
 * Validate and simulate never change anything, so a pull request can be checked before its manifests are applied.
 */
@Service
public class GovernanceCheckService {
  static final int MAX_CASES = 200;

  public record ValidationResponse(boolean valid, int errors, int warnings, List<GovernanceFinding> findings) {
    static ValidationResponse of(List<GovernanceFinding> findings) {
      int errors = (int) findings.stream().filter(GovernanceFinding::isError).count();
      return new ValidationResponse(errors == 0, errors, findings.size() - errors, findings);
    }
  }

  public record SimulationCase(
      String name,
      String system,
      String action,
      Map<String, Object> resource,
      Map<String, Object> context,
      String model,
      /** Optional: ALLOW, DENY or REVIEW. When given, the case passes only if the policy answers this. */
      DecisionStatus expect) {
  }

  public record SimulationRequest(List<Map<String, Object>> resources, List<SimulationCase> cases) {
  }

  public record SimulationResult(
      String name, String system, String action, DecisionStatus status, String rule, String reason,
      DecisionStatus expect, Boolean passed) {
  }

  public record SimulationResponse(boolean passed, List<SimulationResult> results, List<GovernanceFinding> findings) {
  }

  public record GateSummary(ReleaseDecision decision, List<String> blockers, List<String> reviewReasons) {
  }

  public record ReleaseCheck(
      String system,
      UUID systemId,
      ReleaseDecision decision,
      /** For scripts: PASS 0, BLOCKED 1, REVIEW 2. Same contract as GET /api/v1/ci/release-gate. */
      int exitCode,
      String summary,
      PolicyRef policy,
      boolean deploymentBlocking,
      List<GovernanceFinding> findings,
      List<DriftReport> drift,
      GateSummary releaseGate) {
  }

  public record SystemStatus(
      String system,
      UUID systemId,
      int systemVersion,
      String model,
      List<String> tools,
      PolicyRef policy,
      String runtimeEnforcement,
      ValidationResponse validation,
      List<DriftReport> drift,
      long pendingApprovals,
      Map<String, Long> decisionsLast24h) {
  }

  public record GraphNode(String id, String type, String label) {
  }

  public record GraphEdge(String from, String to, String relation) {
  }

  public record Graph(List<GraphNode> nodes, List<GraphEdge> edges) {
  }

  private final GovernanceRegistry registry;
  private final GovernanceDriftService drift;
  private final GovernanceDecisionJpaRepository decisions;
  private final GovernanceApprovalJpaRepository approvals;
  private final AiSystemRepository systems;
  private final ReleaseGateService releaseGate;
  private final EntitlementService entitlements;
  private final AuditService audit;
  private final TenantContext tenantContext;
  private final Clock clock;

  public GovernanceCheckService(
      GovernanceRegistry registry,
      GovernanceDriftService drift,
      GovernanceDecisionJpaRepository decisions,
      GovernanceApprovalJpaRepository approvals,
      AiSystemRepository systems,
      ReleaseGateService releaseGate,
      EntitlementService entitlements,
      AuditService audit,
      TenantContext tenantContext,
      Clock clock) {
    this.registry = registry;
    this.drift = drift;
    this.decisions = decisions;
    this.approvals = approvals;
    this.systems = systems;
    this.releaseGate = releaseGate;
    this.entitlements = entitlements;
    this.audit = audit;
    this.tenantContext = tenantContext;
    this.clock = clock;
  }

  /** A system and policy as they would be if the manifests in a request were applied. */
  private record Resolved(String name, SystemSpec system, PolicySpec policy) {
  }

  /**
   * Validates the applied state ({@code rawManifests} null or empty: one system, or all when {@code system} is
   * null), or the state that would result from applying {@code rawManifests} on top of it.
   */
  @Transactional(readOnly = true)
  public ValidationResponse validate(List<Map<String, Object>> rawManifests, String system) {
    List<GovernanceFinding> findings = new ArrayList<>();
    for (Resolved resolved : resolve(rawManifests, system, findings).values()) {
      findings.addAll(GovernanceValidator.validate(resolved.name(), resolved.system(), resolved.policy()));
    }
    return ValidationResponse.of(findings);
  }

  @Transactional(readOnly = true)
  public SimulationResponse simulate(SimulationRequest request) {
    List<SimulationCase> cases = request == null || request.cases() == null ? List.of() : request.cases();
    if (cases.isEmpty() || cases.size() > MAX_CASES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cases: send 1 to " + MAX_CASES + " cases");
    }
    List<GovernanceFinding> findings = new ArrayList<>();
    Map<String, Resolved> overlay = resolve(request.resources(), null, findings);
    boolean manifestsValid = findings.stream().noneMatch(GovernanceFinding::isError);
    List<SimulationResult> results = new ArrayList<>();
    boolean passed = manifestsValid;
    for (int i = 0; i < cases.size(); i++) {
      SimulationCase test = cases.get(i);
      String name = test.name() == null || test.name().isBlank() ? "case " + (i + 1) : test.name();
      if (test.system() == null || test.action() == null || !PolicySpec.isAction(test.action().trim())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            name + ": system and action (such as payment.refund) are required");
      }
      Resolved resolved = overlay.get(test.system());
      if (resolved == null) {
        GovernanceResourceEntity applied = registry.requireSystem(test.system());
        resolved = new Resolved(applied.name(), GovernanceRegistry.systemSpec(applied),
            registry.activePolicy(applied.systemId()).map(GovernanceRegistry::policySpec).orElse(null));
      }
      PolicyEvaluator.Evaluation evaluation = resolved.policy() == null
          ? PolicyEvaluator.Evaluation.of(DecisionStatus.DENY, "policy",
              "No GovernancePolicy targets this system, so nothing is allowed")
          : PolicyEvaluator.evaluate(resolved.policy(), new PolicyEvaluator.Request(
              test.action().trim(),
              test.resource() == null ? Map.of() : test.resource(),
              test.context() == null ? Map.of() : test.context(),
              test.model() == null || test.model().isBlank() ? null : test.model().trim()));
      Boolean casePassed = test.expect() == null ? null : test.expect() == evaluation.status();
      if (Boolean.FALSE.equals(casePassed)) {
        passed = false;
      }
      results.add(new SimulationResult(name, resolved.name(), test.action().trim(), evaluation.status(),
          evaluation.rule(), evaluation.reason(), test.expect(), casePassed));
    }
    return new SimulationResponse(passed, results, findings);
  }

  /**
   * The systems to check, by name. Manifests in the request win over what is applied; a policy sent without its
   * system is checked against the applied system it targets.
   */
  private Map<String, Resolved> resolve(
      List<Map<String, Object>> rawManifests, String onlySystem, List<GovernanceFinding> findings) {
    Map<String, Resolved> out = new LinkedHashMap<>();
    if (rawManifests == null || rawManifests.isEmpty()) {
      List<GovernanceResourceEntity> applied = onlySystem == null || onlySystem.isBlank()
          ? registry.systems() : List.of(registry.requireSystem(onlySystem));
      for (GovernanceResourceEntity system : applied) {
        out.put(system.name(), new Resolved(system.name(), GovernanceRegistry.systemSpec(system),
            registry.activePolicy(system.systemId()).map(GovernanceRegistry::policySpec).orElse(null)));
      }
      return out;
    }
    List<String> errors = new ArrayList<>();
    List<GovernanceManifest> manifests = GovernanceManifest.parseAll(rawManifests, errors);
    for (String error : errors) {
      int split = error.indexOf(": ");
      findings.add(new GovernanceFinding("MANIFEST_INVALID", GovernanceFinding.ERROR, "",
          split > 0 ? error.substring(0, split) : "manifest", split > 0 ? error.substring(split + 2) : error));
    }
    Map<String, SystemSpec> sentSystems = new LinkedHashMap<>();
    Map<String, PolicySpec> sentPolicies = new LinkedHashMap<>();
    for (GovernanceManifest manifest : manifests) {
      if (manifest.kind() == GovernanceResourceKind.AI_SYSTEM) {
        sentSystems.put(manifest.name(), manifest.system());
      } else if (sentPolicies.putIfAbsent(manifest.policy().targetSystem(), manifest.policy()) != null) {
        findings.add(new GovernanceFinding("POLICY_CONFLICT", GovernanceFinding.ERROR,
            manifest.policy().targetSystem(), "GovernancePolicy/" + manifest.name(),
            "More than one policy in this request targets system '" + manifest.policy().targetSystem()
                + "'. A system has one active policy."));
      }
    }
    Set<String> names = new LinkedHashSet<>(sentSystems.keySet());
    names.addAll(sentPolicies.keySet());
    for (String name : names) {
      Optional<GovernanceResourceEntity> applied = registry.findSystem(name);
      SystemSpec system = sentSystems.get(name);
      if (system == null) {
        if (applied.isEmpty()) {
          findings.add(new GovernanceFinding("TARGET_UNKNOWN", GovernanceFinding.ERROR, name, name,
              "A policy targets system '" + name + "', which is neither in this request nor applied."));
          continue;
        }
        system = GovernanceRegistry.systemSpec(applied.get());
      }
      PolicySpec policy = sentPolicies.get(name);
      if (policy == null && applied.isPresent()) {
        policy = registry.activePolicy(applied.get().systemId()).map(GovernanceRegistry::policySpec).orElse(null);
      }
      out.put(name, new Resolved(name, system, policy));
    }
    return out;
  }

  /**
   * May this system be released? BLOCKED when the release gate blocks, or when the policy's deployment
   * enforcement is {@code blocking} (the default) and validation has an error or a deployment has drifted.
   * With {@code advisory} enforcement those governance problems make the result REVIEW instead.
   */
  @Transactional
  public ReleaseCheck releaseCheck(String systemNameOrId) {
    GovernanceResourceEntity resource = registry.requireSystem(systemNameOrId);
    AiSystem system = systems.findById(resource.systemId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    entitlements.recordGateRun();
    Optional<GovernanceResourceEntity> policyResource = registry.activePolicy(resource.systemId());
    PolicySpec policy = policyResource.map(GovernanceRegistry::policySpec).orElse(null);
    List<GovernanceFinding> findings =
        GovernanceValidator.validate(resource.name(), GovernanceRegistry.systemSpec(resource), policy);
    List<DriftReport> drifted = drift.reports(resource.name()).stream().filter(r -> !r.inSync()).toList();
    ReleaseGateResponse gate = releaseGate.calculate(system);

    boolean blocking = policy == null || policy.deploymentBlocking();
    long errors = findings.stream().filter(GovernanceFinding::isError).count();
    boolean governanceProblem = errors > 0 || !drifted.isEmpty();
    ReleaseDecision decision = gate.decision();
    if (governanceProblem) {
      if (blocking) {
        decision = ReleaseDecision.BLOCKED;
      } else if (decision == ReleaseDecision.PASS) {
        decision = ReleaseDecision.REVIEW;
      }
    }
    List<String> parts = new ArrayList<>();
    if (errors > 0) {
      parts.add(errors + " governance validation error" + (errors == 1 ? "" : "s"));
    }
    if (!drifted.isEmpty()) {
      parts.add("governance drift in " + String.join(", ", drifted.stream().map(DriftReport::environment).toList()));
    }
    if (gate.decision() != ReleaseDecision.PASS) {
      parts.add("release gate " + gate.decision());
    }
    String summary = "Release check " + decision + (parts.isEmpty() ? " - nothing blocks." : " - " + String.join("; ", parts) + ".");
    audit.append(resource.systemId(), "governance.release_check", "ai_system", resource.systemId().toString(), Map.of(
        "decision", decision.name(),
        "validationErrors", (int) errors,
        "drift", !drifted.isEmpty(),
        "releaseGate", gate.decision().name()));
    return new ReleaseCheck(resource.name(), resource.systemId(), decision,
        CiReleaseGateResponse.exitCodeFor(decision), summary,
        policyResource.map(p -> new PolicyRef(p.name(), p.version())).orElse(null), blocking, findings, drifted,
        new GateSummary(gate.decision(), gate.blockers(), gate.reviewReasons()));
  }

  @Transactional(readOnly = true)
  public List<SystemStatus> status(String systemNameOrId) {
    List<GovernanceResourceEntity> selected = systemNameOrId == null || systemNameOrId.isBlank()
        ? registry.systems() : List.of(registry.requireSystem(systemNameOrId));
    UUID tenantId = tenantContext.tenantId();
    Instant now = clock.instant();
    Instant dayAgo = now.minus(Duration.ofHours(24));
    List<GovernanceApprovalEntity> pending =
        approvals.findByTenantIdAndStatusOrderByRequestedAtDesc(tenantId, ApprovalStatus.PENDING, Limit.of(1000));
    List<SystemStatus> out = new ArrayList<>();
    for (GovernanceResourceEntity resource : selected) {
      SystemSpec spec = GovernanceRegistry.systemSpec(resource);
      Optional<GovernanceResourceEntity> policyResource = registry.activePolicy(resource.systemId());
      PolicySpec policy = policyResource.map(GovernanceRegistry::policySpec).orElse(null);
      Map<String, Long> counts = new LinkedHashMap<>();
      for (DecisionStatus status : DecisionStatus.values()) {
        counts.put(status.name(), decisions.countByTenantIdAndSystemIdAndStatusAndCreatedAtGreaterThanEqual(
            tenantId, resource.systemId(), status, dayAgo));
      }
      long waiting = pending.stream()
          .filter(a -> a.systemId().equals(resource.systemId()) && a.statusAt(now) == ApprovalStatus.PENDING)
          .count();
      out.add(new SystemStatus(resource.name(), resource.systemId(), resource.version(), spec.modelRef(),
          spec.tools(), policyResource.map(p -> new PolicyRef(p.name(), p.version())).orElse(null),
          policy == null ? "none" : policy.runtimeEnforced() ? "enforce" : "monitor",
          ValidationResponse.of(GovernanceValidator.validate(resource.name(), spec, policy)),
          drift.reports(resource.name()), waiting, counts));
    }
    return out;
  }

  /**
   * The governance resource graph: which system uses which model and tools, which action each tool performs,
   * and which policy rule decides that action. Built from the applied manifests.
   */
  @Transactional(readOnly = true)
  public Graph graph(String systemNameOrId) {
    List<GovernanceResourceEntity> selected = systemNameOrId == null || systemNameOrId.isBlank()
        ? registry.systems() : List.of(registry.requireSystem(systemNameOrId));
    Map<String, GraphNode> nodes = new LinkedHashMap<>();
    List<GraphEdge> edges = new ArrayList<>();
    for (GovernanceResourceEntity resource : selected) {
      SystemSpec spec = GovernanceRegistry.systemSpec(resource);
      String systemNode = "system:" + resource.name();
      nodes.put(systemNode, new GraphNode(systemNode, "system", resource.name() + " v" + resource.version()));
      if (spec.modelRef() != null) {
        String modelNode = "model:" + spec.modelRef();
        nodes.putIfAbsent(modelNode, new GraphNode(modelNode, "model", spec.modelRef()));
        edges.add(new GraphEdge(systemNode, modelNode, "uses"));
      }
      Optional<GovernanceResourceEntity> policyResource = registry.activePolicy(resource.systemId());
      PolicySpec policy = policyResource.map(GovernanceRegistry::policySpec).orElse(null);
      String policyNode = null;
      if (policyResource.isPresent()) {
        policyNode = "policy:" + policyResource.get().name();
        nodes.put(policyNode, new GraphNode(policyNode, "policy",
            policyResource.get().name() + " v" + policyResource.get().version()));
        edges.add(new GraphEdge(policyNode, systemNode, "governs"));
      }
      for (String tool : spec.tools()) {
        String toolNode = "tool:" + resource.name() + "/" + tool;
        nodes.put(toolNode, new GraphNode(toolNode, "tool", tool));
        edges.add(new GraphEdge(systemNode, toolNode, "has"));
        String action = policy == null ? null : policy.tools().get(tool);
        if (action == null) {
          continue;
        }
        String actionNode = "action:" + resource.name() + "/" + action;
        nodes.putIfAbsent(actionNode, new GraphNode(actionNode, "action", action));
        edges.add(new GraphEdge(toolNode, actionNode, "performs"));
        Optional<PolicySpec.Rule> rule = policy.ruleFor(action);
        String ruleNode = "rule:" + resource.name() + "/" + rule.map(PolicySpec.Rule::action).orElse("default");
        String label = rule
            .map(r -> r.action() + ": " + (r.decision() == DecisionStatus.DENY ? "deny"
                : r.review().isEmpty() ? "allow" : "allow, review when matched"))
            .orElse("default: " + policy.defaultDecision().name().toLowerCase(java.util.Locale.ROOT));
        if (nodes.putIfAbsent(ruleNode, new GraphNode(ruleNode, "rule", label)) == null) {
          edges.add(new GraphEdge(policyNode, ruleNode, "defines"));
        }
        edges.add(new GraphEdge(actionNode, ruleNode, "decided by"));
      }
    }
    return new Graph(List.copyOf(nodes.values()), edges);
  }
}
