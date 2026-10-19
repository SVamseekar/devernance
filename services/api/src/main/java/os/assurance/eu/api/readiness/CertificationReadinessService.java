package os.assurance.eu.api.readiness;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditChainVerifyResponse;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.control.ControlStatus;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.control.SystemControl;
import os.assurance.eu.api.determination.DeterminationRunJpaRepository;
import os.assurance.eu.api.evidence.EvidenceDocument;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.DataContractStatus;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateResponse;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.workflow.ApprovalWorkflow;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import os.assurance.eu.api.workflow.WorkflowStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CertificationReadinessService {
  private final AiSystemRepository systems;
  private final ReleaseGateService releaseGateService;
  private final ControlService controlService;
  private final EvidenceRepository evidenceRepository;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final DeterminationRunJpaRepository determinationRuns;
  private final AuditService auditService;
  private final TenantContext tenantContext;
  private final CertificationReadinessProperties properties;
  private final Clock clock;

  public CertificationReadinessService(
      AiSystemRepository systems,
      ReleaseGateService releaseGateService,
      ControlService controlService,
      EvidenceRepository evidenceRepository,
      ApprovalWorkflowService approvalWorkflowService,
      DeterminationRunJpaRepository determinationRuns,
      AuditService auditService,
      TenantContext tenantContext,
      CertificationReadinessProperties properties,
      Clock clock) {
    this.systems = systems;
    this.releaseGateService = releaseGateService;
    this.controlService = controlService;
    this.evidenceRepository = evidenceRepository;
    this.approvalWorkflowService = approvalWorkflowService;
    this.determinationRuns = determinationRuns;
    this.auditService = auditService;
    this.tenantContext = tenantContext;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public CertificationReadinessResponse assess(UUID systemId) {
    AiSystem system = systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    return assess(system);
  }

  @Transactional(readOnly = true)
  public CertificationReadinessResponse assess(AiSystem system) {
    List<ReadinessGap> gaps = new ArrayList<>();
    List<ReadinessDimensionScore> dimensions = new ArrayList<>();

    ReleaseGateResponse gate = releaseGateService.calculate(system);
    List<SystemControl> controls = controlService.listForSystem(system.id());
    List<EvidenceDocument> evidenceDocs = evidenceRepository.findDocumentsBySystemId(system.id());
    List<ApprovalWorkflow> workflows = approvalWorkflowService.listBySystemId(system.id());
    boolean hasDetermination = determinationRuns
        .findFirstByTenantIdAndSystemIdOrderByCreatedAtDesc(tenantContext.tenantId(), system.id())
        .isPresent();
    AuditChainVerifyResponse chain = auditService.recentVerification();

    dimensions.add(scoreRisk(system, gaps));
    dimensions.add(scoreControls(controls, gaps));
    dimensions.add(scoreEvidence(system, evidenceDocs, gaps));
    dimensions.add(scoreEval(system, gaps));
    dimensions.add(scoreContracts(system, gaps));
    dimensions.add(scoreApprovals(workflows, gaps));
    dimensions.add(scoreOversight(system, controls, gaps));
    dimensions.add(scoreDetermination(hasDetermination, gaps));
    dimensions.add(scoreAuditChain(chain, gaps));

    int totalWeight = properties.totalWeight();
    int earned = dimensions.stream().mapToInt(ReadinessDimensionScore::weightedPoints).sum();
    int score = (int) Math.round((earned * 100.0) / totalWeight);
    score = Math.max(0, Math.min(100, score));

    // Surface release-gate blockers that may not already map to dimensions
    for (String blocker : gate.blockers()) {
      if (blocker == null || blocker.isBlank()) {
        continue;
      }
      String code = "RELEASE_GATE:" + blocker.replace(' ', '_').toUpperCase(Locale.ROOT);
      if (gaps.stream().noneMatch(g -> g.message().equals(blocker) || g.code().equals(code))) {
        gaps.add(new ReadinessGap(
            code,
            ReadinessGapSeverity.CRITICAL,
            blocker,
            "Resolve the release-gate blocker before readiness review.",
            "release_gate"));
      }
    }

    CertificationReadinessStatus status = resolveStatus(score, gaps, gate.decision(), system.riskClass());

    return new CertificationReadinessResponse(
        system.id(),
        system.name(),
        score,
        status,
        CertificationReadinessDisclaimers.PRODUCT_LABEL,
        CertificationReadinessDisclaimers.FULL,
        clock.instant(),
        gate.decision(),
        dimensions,
        gaps);
  }

  @Transactional
  public CertificationReadinessResponse assessAndAudit(UUID systemId) {
    CertificationReadinessResponse report = assess(systemId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("score", report.score());
    payload.put("readinessStatus", report.readinessStatus().name());
    payload.put("gapCount", report.gaps().size());
    payload.put("productLabel", report.productLabel());
    payload.put("releaseDecision", report.releaseDecision().name());
    auditService.append(
        systemId,
        "certification_readiness.assessed",
        "ai_system",
        systemId.toString(),
        payload);
    return report;
  }

  @Transactional
  public CertificationReadinessResponse exportAndAudit(UUID systemId, String format) {
    CertificationReadinessResponse report = assess(systemId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("score", report.score());
    payload.put("readinessStatus", report.readinessStatus().name());
    payload.put("gapCount", report.gaps().size());
    payload.put("format", format == null ? "json" : format);
    payload.put("productLabel", report.productLabel());
    auditService.append(
        systemId,
        "certification_readiness.exported",
        "ai_system",
        systemId.toString(),
        payload);
    return report;
  }

  public byte[] renderPdf(CertificationReadinessResponse report) {
    return CertificationReadinessPdfRenderer.render(report);
  }

  public String pdfFilename(CertificationReadinessResponse report) {
    return CertificationReadinessPdfRenderer.filename(report.systemId(), report.generatedAt());
  }

  private CertificationReadinessStatus resolveStatus(
      int score,
      List<ReadinessGap> gaps,
      ReleaseDecision decision,
      RiskClass riskClass) {
    boolean hasCritical = gaps.stream()
        .anyMatch(g -> g.severity() == ReadinessGapSeverity.CRITICAL);
    boolean hasHigh = gaps.stream()
        .anyMatch(g -> g.severity() == ReadinessGapSeverity.HIGH);

    if (riskClass == RiskClass.PROHIBITED
        || decision == ReleaseDecision.BLOCKED
        || hasCritical
        || score < properties.getNotReadyScoreFloor()) {
      return CertificationReadinessStatus.NOT_READY;
    }
    // READY_FOR_REVIEW: score threshold met and no CRITICAL/HIGH gaps.
    // Residual LOW (or empty) gaps are allowed — human still reviews the report.
    if (score >= properties.getReadyForReviewThreshold() && !hasHigh) {
      return CertificationReadinessStatus.READY_FOR_REVIEW;
    }
    return CertificationReadinessStatus.GAPS;
  }

  private ReadinessDimensionScore scoreRisk(AiSystem system, List<ReadinessGap> gaps) {
    int weight = properties.getWeightRisk();
    int local;
    String summary;
    String status;
    if (system.riskClass() == RiskClass.PROHIBITED) {
      local = 0;
      status = "FAIL";
      summary = "Risk class is PROHIBITED — not eligible for conformity readiness path.";
      gaps.add(new ReadinessGap(
          "RISK_PROHIBITED",
          ReadinessGapSeverity.CRITICAL,
          "System is classified as PROHIBITED",
          "Prohibited systems cannot pursue conformity documentation readiness. Re-evaluate purpose or withdraw.",
          "risk"));
    } else if (system.riskBasis() == null || system.riskBasis().isBlank()) {
      local = 40;
      status = "PARTIAL";
      summary = "Risk class set but risk basis is missing.";
      gaps.add(new ReadinessGap(
          "RISK_BASIS_MISSING",
          ReadinessGapSeverity.HIGH,
          "Risk classification basis is missing",
          "Record a risk basis / rationale on the system registry.",
          "risk"));
    } else {
      local = 100;
      status = "PASS";
      summary = "Risk class " + system.riskClass() + " with recorded basis.";
    }
    return dimension("risk", "Risk classified", weight, local, status, summary);
  }

  private ReadinessDimensionScore scoreControls(List<SystemControl> controls, List<ReadinessGap> gaps) {
    int weight = properties.getWeightControls();
    if (controls.isEmpty()) {
      gaps.add(new ReadinessGap(
          "CONTROLS_NONE",
          ReadinessGapSeverity.MEDIUM,
          "No system controls attached",
          "Attach applicable catalog controls for the system risk class.",
          "controls"));
      return dimension("controls", "Controls coverage", weight, 40, "PARTIAL", "No controls attached.");
    }
    long blocked = controls.stream().filter(c -> c.status() == ControlStatus.BLOCKED).count();
    long review = controls.stream().filter(c -> c.status() == ControlStatus.REVIEW).count();
    long pass = controls.stream().filter(c -> c.status() == ControlStatus.PASS).count();
    int total = controls.size();

    if (blocked > 0) {
      gaps.add(new ReadinessGap(
          "CONTROLS_BLOCKED",
          ReadinessGapSeverity.CRITICAL,
          blocked + " control(s) in BLOCKED status",
          "Remediate blocked controls and set status to PASS or REVIEW after evidence review.",
          "controls"));
      return dimension("controls", "Controls coverage", weight, 0, "FAIL",
          blocked + " blocked / " + total + " controls.");
    }
    if (review > 0) {
      gaps.add(new ReadinessGap(
          "CONTROLS_REVIEW",
          ReadinessGapSeverity.HIGH,
          review + " control(s) still in REVIEW",
          "Complete control review and mark PASS with notes/evidence references.",
          "controls"));
      int local = (int) Math.round(55.0 + (45.0 * pass / total));
      return dimension("controls", "Controls coverage", weight, local, "PARTIAL",
          pass + " PASS, " + review + " REVIEW of " + total + ".");
    }
    return dimension("controls", "Controls coverage", weight, 100, "PASS",
        "All " + total + " controls PASS.");
  }

  private ReadinessDimensionScore scoreEvidence(
      AiSystem system, List<EvidenceDocument> docs, List<ReadinessGap> gaps) {
    int weight = properties.getWeightEvidence();
    int threshold = properties.getEvidencePassThreshold();
    int coverage = system.evidenceCoverage();
    long indexed = docs.stream()
        .filter(d -> "indexed".equalsIgnoreCase(d.ingestionStatus()))
        .count();

    int local = Math.min(100, (int) Math.round((coverage * 100.0) / Math.max(1, threshold)));
    if (indexed == 0) {
      local = Math.min(local, 35);
      gaps.add(new ReadinessGap(
          "EVIDENCE_NOT_INDEXED",
          ReadinessGapSeverity.HIGH,
          "No indexed evidence documents",
          "Index at least one evidence document (policy, DPIA, model card, oversight SOP).",
          "evidence"));
    }
    if (coverage < threshold) {
      gaps.add(new ReadinessGap(
          "EVIDENCE_COVERAGE_LOW",
          coverage < threshold / 2 ? ReadinessGapSeverity.CRITICAL : ReadinessGapSeverity.HIGH,
          "Evidence coverage " + coverage + "% is below threshold " + threshold + "%",
          "Increase evidence coverage by indexing additional technical documentation.",
          "evidence"));
    }
    String status = local >= 100 && indexed > 0 ? "PASS" : local >= 50 ? "PARTIAL" : "FAIL";
    String summary = "Coverage " + coverage + "% · " + indexed + " indexed document(s).";
    return dimension("evidence", "Evidence indexed", weight, local, status, summary);
  }

  private ReadinessDimensionScore scoreEval(AiSystem system, List<ReadinessGap> gaps) {
    int weight = properties.getWeightEval();
    int pass = properties.getEvalPassThreshold();
    int hard = properties.getEvalHardBlockThreshold();
    int score = system.evalScore();
    int local = Math.min(100, (int) Math.round((score * 100.0) / Math.max(1, pass)));

    if (score < hard) {
      gaps.add(new ReadinessGap(
          "EVAL_HARD_BLOCK",
          ReadinessGapSeverity.CRITICAL,
          "Eval score " + score + " is below hard block threshold " + hard,
          "Re-run eval gates until score meets release thresholds.",
          "eval"));
      local = Math.min(local, 20);
    } else if (score < pass) {
      gaps.add(new ReadinessGap(
          "EVAL_BELOW_PASS",
          ReadinessGapSeverity.HIGH,
          "Eval score " + score + " is below pass threshold " + pass,
          "Improve model/prompt quality or expand eval datasets, then re-run gates.",
          "eval"));
    }
    String status = score >= pass ? "PASS" : score >= hard ? "PARTIAL" : "FAIL";
    return dimension("eval", "Eval gate", weight, local, status, "Latest eval score " + score + "%.");
  }

  private ReadinessDimensionScore scoreContracts(AiSystem system, List<ReadinessGap> gaps) {
    int weight = properties.getWeightContracts();
    DataContractStatus status = system.dataContractStatus();
    if (status == DataContractStatus.BREACH) {
      gaps.add(new ReadinessGap(
          "CONTRACT_BREACH",
          ReadinessGapSeverity.CRITICAL,
          "Data contract status is BREACH",
          "Resolve open drift events and restore contract health.",
          "contracts"));
      return dimension("contracts", "Contracts healthy", weight, 0, "FAIL", "BREACH open.");
    }
    if (status == DataContractStatus.WARNING) {
      gaps.add(new ReadinessGap(
          "CONTRACT_WARNING",
          ReadinessGapSeverity.MEDIUM,
          "Data contract status is WARNING",
          "Review warnings and remediate schema/semantic drift before release review.",
          "contracts"));
      return dimension("contracts", "Contracts healthy", weight, 55, "PARTIAL", "WARNING.");
    }
    return dimension("contracts", "Contracts healthy", weight, 100, "PASS", "HEALTHY.");
  }

  private ReadinessDimensionScore scoreApprovals(List<ApprovalWorkflow> workflows, List<ReadinessGap> gaps) {
    int weight = properties.getWeightApprovals();
    if (workflows.isEmpty()) {
      gaps.add(new ReadinessGap(
          "APPROVALS_NONE",
          ReadinessGapSeverity.LOW,
          "No approval workflow cycles recorded",
          "Complete an approval cycle when release review is required.",
          "approvals"));
      return dimension("approvals", "Approvals complete", weight, 70, "PARTIAL",
          "No workflow history.");
    }
    long open = workflows.stream().filter(w -> w.status() == WorkflowStatus.OPEN).count();
    long rejected = workflows.stream().filter(w -> w.status() == WorkflowStatus.REJECTED).count();
    long approved = workflows.stream().filter(w -> w.status() == WorkflowStatus.APPROVED).count();

    if (open > 0) {
      gaps.add(new ReadinessGap(
          "APPROVALS_OPEN",
          ReadinessGapSeverity.HIGH,
          open + " open approval workflow(s)",
          "Complete pending approval stages (engineering, compliance, legal).",
          "approvals"));
      return dimension("approvals", "Approvals complete", weight, 35, "PARTIAL",
          open + " open cycle(s).");
    }
    if (rejected > 0 && approved == 0) {
      gaps.add(new ReadinessGap(
          "APPROVALS_REJECTED",
          ReadinessGapSeverity.HIGH,
          "Latest approval cycles were rejected without an approved cycle",
          "Address rejection rationale and open a new approval cycle.",
          "approvals"));
      return dimension("approvals", "Approvals complete", weight, 25, "FAIL",
          "Rejected without approved cycle.");
    }
    return dimension("approvals", "Approvals complete", weight, 100, "PASS",
        approved + " approved cycle(s).");
  }

  private ReadinessDimensionScore scoreOversight(
      AiSystem system, List<SystemControl> controls, List<ReadinessGap> gaps) {
    int weight = properties.getWeightOversight();
    boolean highRisk = system.riskClass() == RiskClass.HIGH;
    boolean oversightGap = system.openGaps().stream()
        .anyMatch(g -> g != null && g.toLowerCase(Locale.ROOT).contains("oversight"));

    boolean oversightControlBlockedOrReview = controls.stream()
        .filter(c -> c.controlCode() != null
            && (c.controlCode().toUpperCase(Locale.ROOT).contains("OVERSIGHT")
                || c.controlName() != null
                    && c.controlName().toLowerCase(Locale.ROOT).contains("oversight")))
        .anyMatch(c -> c.status() != ControlStatus.PASS);

    if (!highRisk) {
      if (oversightGap) {
        gaps.add(new ReadinessGap(
            "OVERSIGHT_GAP_NOTED",
            ReadinessGapSeverity.MEDIUM,
            "Open oversight-related gap on non-high-risk system",
            "Close the oversight gap or document why oversight is N/A for this risk class.",
            "oversight"));
        return dimension("oversight", "Oversight evidence", weight, 70, "PARTIAL",
            "Oversight gap noted (non-high-risk).");
      }
      return dimension("oversight", "Oversight evidence", weight, 100, "PASS",
          "Oversight not required at this risk class (or no open oversight gap).");
    }

    if (oversightGap || oversightControlBlockedOrReview) {
      gaps.add(new ReadinessGap(
          "OVERSIGHT_REQUIRED",
          ReadinessGapSeverity.CRITICAL,
          "High-risk system is missing human oversight evidence",
          "Attach human-oversight SOP evidence and mark the oversight control PASS.",
          "oversight"));
      return dimension("oversight", "Oversight evidence", weight, 0, "FAIL",
          "High-risk oversight gap.");
    }
    return dimension("oversight", "Oversight evidence", weight, 100, "PASS",
        "High-risk oversight evidence present.");
  }

  private ReadinessDimensionScore scoreDetermination(boolean hasRun, List<ReadinessGap> gaps) {
    int weight = properties.getWeightDetermination();
    if (!hasRun) {
      gaps.add(new ReadinessGap(
          "DETERMINATION_MISSING",
          ReadinessGapSeverity.HIGH,
          "No assisted obligation determination run recorded",
          "Run the assisted obligation map wizard and record a determination run.",
          "determination"));
      return dimension("determination", "Determination run present", weight, 0, "FAIL",
          "No determination run.");
    }
    return dimension("determination", "Determination run present", weight, 100, "PASS",
        "Latest assisted determination run present.");
  }

  private ReadinessDimensionScore scoreAuditChain(AuditChainVerifyResponse chain, List<ReadinessGap> gaps) {
    int weight = properties.getWeightAuditChain();
    if (chain == null || !chain.valid()) {
      gaps.add(new ReadinessGap(
          "AUDIT_CHAIN_INVALID",
          ReadinessGapSeverity.CRITICAL,
          "Audit hash chain verification failed",
          "Investigate audit chain integrity via GET /api/v1/audit/verify before export.",
          "audit_chain"));
      return dimension("audit_chain", "Audit chain valid", weight, 0, "FAIL",
          "Chain invalid (checked=" + (chain == null ? 0 : chain.checkedCount()) + ").");
    }
    if (chain.checkedCount() == 0) {
      gaps.add(new ReadinessGap(
          "AUDIT_CHAIN_EMPTY",
          ReadinessGapSeverity.LOW,
          "Audit chain has no events yet",
          "Normal for brand-new tenants; activity will populate the chain.",
          "audit_chain"));
      return dimension("audit_chain", "Audit chain valid", weight, 80, "PARTIAL",
          "Chain valid but empty.");
    }
    return dimension("audit_chain", "Audit chain valid", weight, 100, "PASS",
        "Chain valid (" + chain.checkedCount() + " events).");
  }

  private ReadinessDimensionScore dimension(
      String code, String label, int weight, int localScore, String status, String summary) {
    int clamped = Math.max(0, Math.min(100, localScore));
    int weighted = (int) Math.round((clamped / 100.0) * weight);
    return new ReadinessDimensionScore(code, label, weight, clamped, weighted, status, summary);
  }
}
