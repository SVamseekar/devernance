package os.assurance.eu.api.governance;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.governance.GovernanceAuthorizationService.AuthorizeRequest;
import os.assurance.eu.api.governance.GovernanceAuthorizationService.DecisionQuery;
import os.assurance.eu.api.governance.GovernanceAuthorizationService.ExecutionRequest;
import os.assurance.eu.api.governance.GovernanceAuthorizationService.UnauthorizedExecutionException;
import os.assurance.eu.api.governance.GovernanceAuthorizationService.VerifyRequest;
import os.assurance.eu.api.governance.GovernanceViews.ApprovalView;
import os.assurance.eu.api.governance.GovernanceViews.DecisionView;
import os.assurance.eu.api.governance.GovernanceViews.ResourceView;
import os.assurance.eu.api.governance.GovernanceViews.TokenVerification;
import os.assurance.eu.api.tenant.SessionOnly;
import os.assurance.eu.api.tenant.TenantContext;

/**
 * The governance runtime API. docs/GOVERNANCE_PLATFORM.md explains the model; docs/API.md lists every route.
 *
 * <p>Routes by purpose:
 * <ul>
 *   <li>Define: {@code POST /apply}, {@code GET /resources}</li>
 *   <li>Check before release: {@code POST /validate}, {@code POST /simulate}, {@code GET /release-check}</li>
 *   <li>Enforce at runtime: {@code POST /authorize}, {@code GET /decisions/{id}},
 *       {@code POST /decisions/{id}/execution}, {@code POST /authorizations/verify}</li>
 *   <li>Human approval: {@code GET /approvals}, {@code POST /approvals/{id}/approve|reject}</li>
 *   <li>Reconcile: {@code POST /observations}, {@code GET /drift}</li>
 *   <li>Verify: {@code GET /decisions}, {@code GET /evidence-bundle}, {@code GET /closure}</li>
 *   <li>Overview: {@code GET /status}, {@code GET /graph}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {
  private final GovernanceApplyService applyService;
  private final GovernanceAuthorizationService authorizationService;
  private final GovernanceCheckService checkService;
  private final GovernanceDriftService driftService;
  private final GovernanceEvidenceService evidenceService;
  private final GovernanceResourceJpaRepository resources;
  private final GovernanceRegistry registry;
  private final TenantContext tenantContext;

  public GovernanceController(
      GovernanceApplyService applyService,
      GovernanceAuthorizationService authorizationService,
      GovernanceCheckService checkService,
      GovernanceDriftService driftService,
      GovernanceEvidenceService evidenceService,
      GovernanceResourceJpaRepository resources,
      GovernanceRegistry registry,
      TenantContext tenantContext) {
    this.applyService = applyService;
    this.authorizationService = authorizationService;
    this.checkService = checkService;
    this.driftService = driftService;
    this.evidenceService = evidenceService;
    this.resources = resources;
    this.registry = registry;
    this.tenantContext = tenantContext;
  }

  public record ManifestsRequest(List<Map<String, Object>> resources) {
  }

  public record ApprovalAnswer(String rationale) {
  }

  // ---- Define -------------------------------------------------------------------------------------------

  @PostMapping("/apply")
  public GovernanceApplyService.ApplyResponse apply(@RequestBody ManifestsRequest request) {
    return applyService.apply(request == null ? null : request.resources());
  }

  /** Applied manifests of one system, newest first; every version, or only the active ones. */
  @GetMapping("/resources")
  public List<ResourceView> resources(
      @RequestParam String system, @RequestParam(defaultValue = "true") boolean activeOnly) {
    GovernanceResourceEntity resource = registry.requireSystem(system);
    return resources.findAllByTenantIdAndSystemIdOrderByCreatedAtDesc(tenantContext.tenantId(), resource.systemId())
        .stream().filter(r -> !activeOnly || r.active()).map(ResourceView::of).toList();
  }

  // ---- Check before release -----------------------------------------------------------------------------

  /** Dry run: validates the manifests in the body against what is applied, changing nothing. */
  @PostMapping("/validate")
  public GovernanceCheckService.ValidationResponse validateManifests(@RequestBody ManifestsRequest request) {
    if (request == null || request.resources() == null || request.resources().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resources: send at least one manifest");
    }
    return checkService.validate(request.resources(), null);
  }

  /** Validates what is applied: one system, or every governed system. */
  @GetMapping("/validate")
  public GovernanceCheckService.ValidationResponse validateApplied(@RequestParam(required = false) String system) {
    return checkService.validate(null, system);
  }

  @PostMapping("/simulate")
  public GovernanceCheckService.SimulationResponse simulate(
      @RequestBody GovernanceCheckService.SimulationRequest request) {
    return checkService.simulate(request);
  }

  @GetMapping("/release-check")
  public GovernanceCheckService.ReleaseCheck releaseCheck(@RequestParam String system) {
    return checkService.releaseCheck(system);
  }

  // ---- Enforce at runtime -------------------------------------------------------------------------------

  @PostMapping("/authorize")
  public DecisionView authorize(HttpServletRequest http, @RequestBody AuthorizeRequest request) {
    return authorizationService.authorize(request, GovernanceCaller.from(http, tenantContext.actorId()));
  }

  @GetMapping("/decisions/{decisionId}")
  public DecisionView decision(HttpServletRequest http, @PathVariable UUID decisionId) {
    return authorizationService.decision(decisionId, GovernanceCaller.from(http, tenantContext.actorId()));
  }

  @PostMapping("/decisions/{decisionId}/execution")
  public DecisionView recordExecution(
      HttpServletRequest http, @PathVariable UUID decisionId, @RequestBody ExecutionRequest request) {
    return authorizationService.recordExecution(
        decisionId, request, GovernanceCaller.from(http, tenantContext.actorId()));
  }

  @PostMapping("/authorizations/verify")
  public TokenVerification verify(@RequestBody VerifyRequest request) {
    return authorizationService.verify(request);
  }

  // ---- Human approval -----------------------------------------------------------------------------------

  @GetMapping("/approvals")
  public List<ApprovalView> approvals(
      @RequestParam(required = false) ApprovalStatus status, @RequestParam(required = false) Integer limit) {
    return authorizationService.approvals(status, limit);
  }

  /** A person decides, in a signed-in session: an API key (an agent's credential) can never approve. */
  @PostMapping("/approvals/{approvalId}/approve")
  public ApprovalView approve(
      HttpServletRequest http, @PathVariable UUID approvalId, @RequestBody(required = false) ApprovalAnswer answer) {
    SessionOnly.require(http);
    return authorizationService.decideApproval(approvalId, true, answer == null ? null : answer.rationale());
  }

  @PostMapping("/approvals/{approvalId}/reject")
  public ApprovalView reject(
      HttpServletRequest http, @PathVariable UUID approvalId, @RequestBody(required = false) ApprovalAnswer answer) {
    SessionOnly.require(http);
    return authorizationService.decideApproval(approvalId, false, answer == null ? null : answer.rationale());
  }

  // ---- Reconcile ----------------------------------------------------------------------------------------

  @PostMapping("/observations")
  public GovernanceDriftService.DriftReport observe(@RequestBody GovernanceDriftService.ObservationRequest request) {
    return driftService.record(request);
  }

  @GetMapping("/drift")
  public List<GovernanceDriftService.DriftReport> drift(@RequestParam(required = false) String system) {
    return driftService.reports(system);
  }

  // ---- Verify -------------------------------------------------------------------------------------------

  @GetMapping("/decisions")
  public List<DecisionView> decisions(
      @RequestParam(required = false) String system,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) DecisionStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Integer limit) {
    return authorizationService.decisions(new DecisionQuery(system, action, status, from, to, limit));
  }

  @GetMapping("/evidence-bundle")
  public Map<String, Object> evidenceBundle(
      @RequestParam String system,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return evidenceService.bundle(system, from, to);
  }

  @GetMapping("/closure")
  public GovernanceEvidenceService.ClosureReport closure(
      @RequestParam String system,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return evidenceService.closure(system, from, to);
  }

  // ---- Overview -----------------------------------------------------------------------------------------

  @GetMapping("/status")
  public List<GovernanceCheckService.SystemStatus> status(@RequestParam(required = false) String system) {
    return checkService.status(system);
  }

  @GetMapping("/graph")
  public GovernanceCheckService.Graph graph(@RequestParam(required = false) String system) {
    return checkService.graph(system);
  }

  // ---- Errors -------------------------------------------------------------------------------------------

  @ExceptionHandler(UnauthorizedExecutionException.class)
  public ResponseEntity<Map<String, Object>> unauthorizedExecution(UnauthorizedExecutionException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("status", 409, "error", "Conflict", "message", ex.getMessage()));
  }

  /** Two applies of the same resource at once: the second one's version number is taken. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> concurrentApply(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("status", 409, "error", "Conflict",
            "message", "Another change was applied at the same time. Run the command again."));
  }
}
