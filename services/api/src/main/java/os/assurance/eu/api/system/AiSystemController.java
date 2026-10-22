package os.assurance.eu.api.system;

import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.billing.Feature;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.observability.AssuranceMetrics;
import os.assurance.eu.api.observability.NfrMetrics;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.UserRole;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import os.assurance.eu.api.workflow.WorkflowTrigger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/systems")
public class AiSystemController {
  private final EntitlementService entitlements;
  private final AiSystemRepository repository;
  private final ReleaseGateService releaseGateService;
  private final AuditService auditService;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final ControlService controlService;
  private final TenantAuthorizationService authorizationService;
  private final NfrMetrics nfrMetrics;
  private final AssuranceMetrics assuranceMetrics;
  private final EvidencePackService evidencePackService;
  private final LinkedControlReopenService linkedControlReopenService;
  private final GateInputService gateInputs;

  public AiSystemController(
      AiSystemRepository repository,
      ReleaseGateService releaseGateService,
      AuditService auditService,
      ApprovalWorkflowService approvalWorkflowService,
      ControlService controlService,
      TenantAuthorizationService authorizationService,
      NfrMetrics nfrMetrics,
      AssuranceMetrics assuranceMetrics,
      EvidencePackService evidencePackService,
      LinkedControlReopenService linkedControlReopenService,
      GateInputService gateInputs,
      EntitlementService entitlements) {
    this.entitlements = entitlements;
    this.repository = repository;
    this.releaseGateService = releaseGateService;
    this.auditService = auditService;
    this.approvalWorkflowService = approvalWorkflowService;
    this.controlService = controlService;
    this.authorizationService = authorizationService;
    this.nfrMetrics = nfrMetrics;
    this.assuranceMetrics = assuranceMetrics;
    this.evidencePackService = evidencePackService;
    this.linkedControlReopenService = linkedControlReopenService;
    this.gateInputs = gateInputs;
  }

  @GetMapping
  public List<AiSystem> listSystems() {
    return nfrMetrics.recordRegistryRead("list", repository::findAll);
  }

  @GetMapping("/{systemId}")
  public AiSystem getSystem(@PathVariable UUID systemId) {
    return nfrMetrics.recordRegistryRead("get", () -> repository.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found")));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateAiSystemResponse createSystem(@Valid @RequestBody CreateAiSystemRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);
    entitlements.requireCanCreateSystem();
    rejectComputedGateFields(request.evidenceCoverage(), request.evalScore(), request.dataContractStatus());
    Instant now = Instant.now();
    int coverage = request.evidenceCoverage() == null ? 0 : request.evidenceCoverage();
    int evalScore = request.evalScore() == null ? 0 : request.evalScore();
    DataContractStatus contract = request.dataContractStatus() == null
        ? DataContractStatus.WARNING
        : request.dataContractStatus();
    if (!gateInputs.manualInputs()) {
      coverage = 0;
      evalScore = 0;
      contract = request.riskClass() == RiskClass.HIGH ? DataContractStatus.WARNING : DataContractStatus.HEALTHY;
    }
    AiSystem draft = new AiSystem(
        UUID.randomUUID(),
        request.name(),
        request.owner(),
        request.purpose(),
        request.riskClass(),
        request.riskBasis(),
        request.deploymentRegion(),
        coverage,
        evalScore,
        contract,
        ReleaseDecision.REVIEW,
        request.openGaps() == null ? List.of() : new ArrayList<>(request.openGaps()),
        request.vendorName(),
        request.modelName(),
        request.modelVersion(),
        request.dataSources() == null ? List.of() : new ArrayList<>(request.dataSources()),
        request.sector(),
        request.decisionImpact(),
        request.affectedUsers() == null ? List.of() : new ArrayList<>(request.affectedUsers()),
        now,
        now);
    if (!gateInputs.manualInputs()) {
      draft = gateInputs.recompute(draft);
    }
    AiSystem saved = saveWithCalculatedDecision(draft);
    controlService.attachApplicableControls(saved);
    // recompute after control attach so CONTROL:* blockers apply
    saved = saveWithCalculatedDecision(saved);
    auditService.append(
        saved.id(),
        "ai_system.created",
        "ai_system",
        saved.id().toString(),
        Map.of(
            "name", saved.name(),
            "releaseDecision", saved.releaseDecision(),
            "vendorName", saved.vendorName() == null ? "" : saved.vendorName(),
            "modelName", saved.modelName() == null ? "" : saved.modelName()));
    approvalWorkflowService.openCycle(saved, WorkflowTrigger.SYSTEM_CREATED);
    return new CreateAiSystemResponse(saved.id(), saved.releaseDecision(), saved.createdAt());
  }

  @PatchMapping("/{systemId}")
  public AiSystem updateSystem(
      @PathVariable UUID systemId,
      @Valid @RequestBody UpdateAiSystemRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);
    entitlements.requireSystemWritable(systemId);
    rejectComputedGateFields(request.evidenceCoverage(), request.evalScore(), request.dataContractStatus());
    AiSystem existing = getSystem(systemId);
    requireReviewerToLowerRisk(existing.riskClass(), request.riskClass(), UserRole.ADMIN, UserRole.COMPLIANCE_OFFICER);
    List<String> removedGapsForAudit = List.of();
    if (!gateInputs.manualInputs() && request.openGaps() != null) {
      List<String> removed = existing.openGaps().stream().filter(g -> !request.openGaps().contains(g)).toList();
      if (!removed.isEmpty()) {
        UserRole role = authorizationService.currentRole();
        if (role != UserRole.ADMIN && role != UserRole.COMPLIANCE_OFFICER) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins and compliance officers can close gaps");
        }
        removedGapsForAudit = removed;
      }
    }
    AiSystem draft = new AiSystem(
        existing.id(),
        request.name() == null ? existing.name() : request.name(),
        request.owner() == null ? existing.owner() : request.owner(),
        request.purpose() == null ? existing.purpose() : request.purpose(),
        request.riskClass() == null ? existing.riskClass() : request.riskClass(),
        request.riskBasis() == null ? existing.riskBasis() : request.riskBasis(),
        request.deploymentRegion() == null ? existing.deploymentRegion() : request.deploymentRegion(),
        request.evidenceCoverage() == null ? existing.evidenceCoverage() : request.evidenceCoverage(),
        request.evalScore() == null ? existing.evalScore() : request.evalScore(),
        request.dataContractStatus() == null ? existing.dataContractStatus() : request.dataContractStatus(),
        existing.releaseDecision(),
        request.openGaps() == null ? existing.openGaps() : new ArrayList<>(request.openGaps()),
        request.vendorName() == null ? existing.vendorName() : request.vendorName(),
        request.modelName() == null ? existing.modelName() : request.modelName(),
        request.modelVersion() == null ? existing.modelVersion() : request.modelVersion(),
        request.dataSources() == null ? existing.dataSources() : new ArrayList<>(request.dataSources()),
        request.sector() == null ? existing.sector() : request.sector(),
        request.decisionImpact() == null ? existing.decisionImpact() : request.decisionImpact(),
        request.affectedUsers() == null ? existing.affectedUsers() : new ArrayList<>(request.affectedUsers()),
        existing.createdAt(),
        Instant.now());
    boolean riskChanged = request.riskClass() != null && request.riskClass() != existing.riskClass();
    boolean sectorChanged = request.sector() != null
        && !request.sector().equalsIgnoreCase(existing.sector() == null ? "" : existing.sector());
    if (riskChanged || sectorChanged) {
      controlService.attachApplicableControls(draft);
    }
    linkedControlReopenService.reopenIfSensitiveChange(existing, request);
    if (!gateInputs.manualInputs()) {
      draft = gateInputs.recompute(draft);
    }
    AiSystem saved = saveWithCalculatedDecision(draft);
    Map<String, Object> auditPayload = new LinkedHashMap<>();
    auditPayload.put("releaseDecision", saved.releaseDecision());
    auditPayload.put("gapsClosed", removedGapsForAudit);
    if (riskChanged) {
      auditPayload.put("previousRiskClass", existing.riskClass());
      auditPayload.put("riskClass", saved.riskClass());
    }
    auditService.append(saved.id(), "ai_system.updated", "ai_system", saved.id().toString(), auditPayload);
    return saved;
  }

  /**
   * Lowering a risk class lowers the evidence the gate requires, so the people who want the release (engineering)
   * cannot do it alone: only the listed reviewer roles may. Raising it, or keeping it, is open to every editor.
   */
  private void requireReviewerToLowerRisk(RiskClass current, RiskClass requested, UserRole... reviewers) {
    if (requested == null || current == null || requested.ordinal() >= current.ordinal()) {
      return;
    }
    UserRole role = authorizationService.currentRole();
    if (!List.of(reviewers).contains(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Lowering a risk class needs a reviewer role: " + List.of(reviewers));
    }
  }

  @PostMapping("/{systemId}/risk-classification")
  public RiskClassificationResponse classifyRisk(
      @PathVariable UUID systemId,
      @Valid @RequestBody RiskClassificationRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN,
        UserRole.AI_ENGINEERING_LEAD,
        UserRole.COMPLIANCE_OFFICER,
        UserRole.LEGAL_COUNSEL);
    entitlements.requireSystemWritable(systemId);
    AiSystem existing = getSystem(systemId);
    requireReviewerToLowerRisk(existing.riskClass(), request.riskClass(),
        UserRole.ADMIN, UserRole.COMPLIANCE_OFFICER, UserRole.LEGAL_COUNSEL);
    List<String> openGaps = new ArrayList<>(existing.openGaps());
    if (request.humanOversightRequired() && openGaps.stream().noneMatch(this::isOversightGap)) {
      openGaps.add("Human oversight evidence required");
    }
    AiSystem draft = new AiSystem(
        existing.id(),
        existing.name(),
        existing.owner(),
        existing.purpose(),
        request.riskClass(),
        request.basis(),
        existing.deploymentRegion(),
        existing.evidenceCoverage(),
        existing.evalScore(),
        existing.dataContractStatus(),
        existing.releaseDecision(),
        openGaps,
        existing.vendorName(),
        existing.modelName(),
        existing.modelVersion(),
        existing.dataSources(),
        request.sector() == null ? existing.sector() : request.sector(),
        request.decisionImpact() == null ? existing.decisionImpact() : request.decisionImpact(),
        request.affectedUsers() == null ? existing.affectedUsers() : new ArrayList<>(request.affectedUsers()),
        existing.createdAt(),
        Instant.now());
    if (!gateInputs.manualInputs()) {
      draft = gateInputs.recompute(draft);
    }
    controlService.attachApplicableControls(draft);
    AiSystem saved = saveWithCalculatedDecision(draft);
    auditService.append(
        saved.id(),
        "risk_classification.updated",
        "ai_system",
        saved.id().toString(),
        Map.of(
            "previousRiskClass", existing.riskClass(),
            "riskClass", saved.riskClass(),
            "humanOversightRequired", request.humanOversightRequired(),
            "affectedUsers", saved.affectedUsers(),
            "sector", saved.sector() == null ? "" : saved.sector(),
            "decisionImpact", saved.decisionImpact() == null ? "" : saved.decisionImpact()));
    approvalWorkflowService.openCycle(saved, WorkflowTrigger.RISK_RECLASSIFIED);
    return new RiskClassificationResponse(
        saved.id(),
        saved.riskClass(),
        saved.riskBasis(),
        request.humanOversightRequired(),
        saved.releaseDecision(),
        saved.updatedAt());
  }

  @GetMapping("/{systemId}/release-gate")
  public ReleaseGateResponse getReleaseGate(@PathVariable UUID systemId) {
    AiSystem system = getSystem(systemId);
    ReleaseGateResponse response = releaseGateService.calculate(system);
    assuranceMetrics.releaseGateDecision(response.decision());
    auditService.append(
        system.id(),
        "release_gate.calculated",
        "ai_system",
        system.id().toString(),
        Map.of("decision", response.decision(), "blockers", response.blockers()));
    return response;
  }

  /**
   * Primary sealed JSON evidence pack (PRD MVP). Includes contentSha256 seal fields.
   */
  @GetMapping("/{systemId}/evgraph-artifacts")
  public Map<String, Object> getEvgraphArtifacts(@PathVariable UUID systemId) {
    requireEvidencePackRole();
    try {
      return evidencePackService.evgraphArtifactFiles(systemId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  @GetMapping("/{systemId}/evidence-pack")
  public EvidencePackResponse getEvidencePack(@PathVariable UUID systemId) {
    requireEvidencePackRole();
    try {
      return evidencePackService.buildAndExport(systemId, "json");
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  /**
   * Phase 6 PDF export of the same sealed pack. Prefer JSON for machine verification.
   * Route: {@code GET /api/v1/systems/{id}/evidence-pack.pdf}
   */
  @GetMapping(value = "/{systemId}/evidence-pack.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> getEvidencePackPdf(@PathVariable UUID systemId) {
    requireEvidencePackRole();
    entitlements.requireFeature(Feature.SIGNED_PDF);
    try {
      EvidencePackResponse pack = evidencePackService.buildAndExport(systemId, "pdf");
      AiSystem system = getSystem(systemId);
      byte[] pdf = evidencePackService.renderPdf(pack, system.name());
      String filename = evidencePackService.pdfFilename(pack);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
          .header("X-Content-Sha256", pack.contentSha256())
          .header("X-Evidence-Signature", pack.signature())
          .contentType(MediaType.APPLICATION_PDF)
          .body(pdf);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  private void requireEvidencePackRole() {
    authorizationService.requireAnyRole(
        UserRole.ADMIN,
        UserRole.AI_ENGINEERING_LEAD,
        UserRole.COMPLIANCE_OFFICER,
        UserRole.LEGAL_COUNSEL,
        UserRole.AUDITOR);
  }

  private void rejectComputedGateFields(
      Integer evidenceCoverage, Integer evalScore, DataContractStatus dataContractStatus) {
    if (!gateInputs.manualInputs()
        && (evidenceCoverage != null || evalScore != null || dataContractStatus != null)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "evidenceCoverage, evalScore, and dataContractStatus are computed from evidence, eval runs, and data contracts");
    }
  }

  private AiSystem saveWithCalculatedDecision(AiSystem draft) {
    ReleaseDecision decision = releaseGateService.calculate(draft).decision();
    return repository.save(new AiSystem(
        draft.id(),
        draft.name(),
        draft.owner(),
        draft.purpose(),
        draft.riskClass(),
        draft.riskBasis(),
        draft.deploymentRegion(),
        draft.evidenceCoverage(),
        draft.evalScore(),
        draft.dataContractStatus(),
        decision,
        draft.openGaps(),
        draft.vendorName(),
        draft.modelName(),
        draft.modelVersion(),
        draft.dataSources(),
        draft.sector(),
        draft.decisionImpact(),
        draft.affectedUsers(),
        draft.createdAt(),
        draft.updatedAt()));
  }

  private boolean isOversightGap(String gap) {
    return gap.toLowerCase().contains("oversight");
  }
}
