package os.assurance.eu.api.system;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.assessment.AssessmentService;
import os.assurance.eu.api.audit.AuditEvent;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.auth.JwtService;
import os.assurance.eu.api.corpus.CorpusQueryService;
import os.assurance.eu.api.contract.DataContract;
import os.assurance.eu.api.contract.DataContractService;
import os.assurance.eu.api.contract.DriftEvent;
import os.assurance.eu.api.contract.DriftStatus;
import os.assurance.eu.api.determination.DeterminationService;
import os.assurance.eu.api.eval.EvalRun;
import os.assurance.eu.api.eval.EvalRunRepository;
import os.assurance.eu.api.evidence.EvidenceDocument;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.framework.CrosswalkService;
import os.assurance.eu.api.proposal.MappingProposalService;
import os.assurance.eu.api.publicclaims.PublicClaimsCatalog;
import os.assurance.eu.api.publicclaims.PublicClaimsService;
import os.assurance.eu.api.workflow.ApprovalStage;
import os.assurance.eu.api.workflow.ApprovalWorkflow;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EvidencePackService {
  public static final String PACK_VERSION = "1.2";

  private final AiSystemRepository repository;
  private final ReleaseGateService releaseGateService;
  private final AuditService auditService;
  private final DataContractService dataContractService;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final DeterminationService determinationService;
  private final PublicClaimsService publicClaimsService;
  private final MappingProposalService mappingProposals;
  private final CorpusQueryService corpus;
  private final EvgraphCli evgraphCli;
  private final AssessmentService assessment;
  private final Clock clock;
  private final JwtService jwtService;
  private final CrosswalkService crosswalks;
  private final EntitlementService entitlements;
  private final EvidenceRepository evidenceDocuments;
  private final EvalRunRepository evalRunHistory;
  private final String generator;

  public EvidencePackService(
      AiSystemRepository repository,
      ReleaseGateService releaseGateService,
      AuditService auditService,
      DataContractService dataContractService,
      ApprovalWorkflowService approvalWorkflowService,
      DeterminationService determinationService,
      PublicClaimsService publicClaimsService,
      MappingProposalService mappingProposals,
      CorpusQueryService corpus,
      EvgraphCli evgraphCli,
      AssessmentService assessment,
      Clock clock,
      JwtService jwtService,
      CrosswalkService crosswalks,
      EntitlementService entitlements,
      EvidenceRepository evidenceDocuments,
      EvalRunRepository evalRunHistory,
      @Value("${assurance.evidence-pack.generator:devernance-api/0.1.0}") String generator) {
    this.repository = repository;
    this.releaseGateService = releaseGateService;
    this.auditService = auditService;
    this.dataContractService = dataContractService;
    this.approvalWorkflowService = approvalWorkflowService;
    this.determinationService = determinationService;
    this.publicClaimsService = publicClaimsService;
    this.mappingProposals = mappingProposals;
    this.corpus = corpus;
    this.evgraphCli = evgraphCli;
    this.assessment = assessment;
    this.clock = clock;
    this.jwtService = jwtService;
    this.crosswalks = crosswalks;
    this.entitlements = entitlements;
    this.evidenceDocuments = evidenceDocuments;
    this.evalRunHistory = evalRunHistory;
    this.generator = generator;
  }

  /**
   * Not one transaction: the evgraph scan below runs external processes for up to a minute, and holding a
   * pooled connection that long starves other requests. Each read runs in its own short transaction, and the
   * export is recorded with the audit append's own.
   */
  public EvidencePackResponse buildAndExport(UUID systemId, String exportFormat) {
    AiSystem system = repository.findById(systemId)
        .orElseThrow(() -> new IllegalArgumentException("AI system not found"));
    ReleaseGateResponse releaseGate = releaseGateService.calculate(system);

    Instant generatedAt = clock.instant();
    String auditChainHead = auditService.chainHeadHash().orElse(null);
    List<AuditEvent> auditEvents = auditService.findBySystemId(system.id());
    List<Map<String, Object>> dataContracts = dataContractService.listContracts(system.id()).stream()
        .map(contract -> contractEvidence(system, contract))
        .toList();
    List<Map<String, Object>> approvals = approvalWorkflowService.listBySystemId(system.id()).stream()
        .map(this::workflowEvidence)
        .toList();
    Map<String, Object> determination = determinationService.latestSnapshotForPack(system.id());
    Map<String, Object> evgraphArtifacts = evgraphArtifacts(system, approvals);
    String corpusVersion = corpus.currentVersionHash();
    List<Map<String, Object>> acceptedLinks = new java.util.ArrayList<>();
    List<Map<String, Object>> queue = new java.util.ArrayList<>();
    for (MappingProposalService.ProposalView row : mappingProposals.list(system.id()).items()) {
      if ("ACCEPTED".equals(row.status())) {
        acceptedLinks.add(linkRow(row));
      } else {
        queue.add(queueRow(row));
      }
    }
    Map<String, Object> acceptedArtifacts = acceptedArtifacts(evgraphArtifacts);
    List<Map<String, Object>> currentGaps = evgraphCli.currentGaps(acceptedArtifacts);
    Map<String, Integer> evidenceCounts = assessment.counts(system.id());
    Map<String, Object> monitoringPlan = MonitoringPlan.standIn();
    Map<String, Object> riskClassification = riskClassification(system);
    // Inside the seal: which documents back the coverage figure (with their checksums) and which eval runs back
    // the score, so a buyer can check both without trusting an unsigned index.
    List<Map<String, Object>> evidence = List.of(Map.of(
        "coverage", system.evidenceCoverage(),
        "openGaps", system.openGaps(),
        "documents", evidenceDocuments.findDocumentsBySystemId(system.id()).stream()
            .map(EvidencePackService::documentRow)
            .toList()));
    List<Map<String, Object>> evalRuns = List.of(Map.of(
        "latestScore", system.evalScore(),
        "releaseDecision", system.releaseDecision(),
        "runs", evalRunHistory.findRecentCompleted(system.id()).stream()
            .map(EvidencePackService::evalRunRow)
            .toList()));

    Map<String, Object> sealPayload = EvidencePackSealer.sealPayload(
        PACK_VERSION,
        generator,
        auditChainHead,
        system.id(),
        generatedAt,
        releaseGate.decision(),
        riskClassification,
        evidence,
        evalRuns,
        dataContracts,
        approvals,
        auditEvents,
        determination,
        Map.of(),
        evgraphArtifacts,
        corpusVersion,
        acceptedLinks,
        queue,
        currentGaps,
        acceptedArtifacts,
        evidenceCounts,
        monitoringPlan);
    Map<String, Object> frameworks = frameworks(system.id());
    if (frameworks != null) {
      sealPayload.put("frameworks", frameworks);
    }
    String contentSha256 = EvidencePackSealer.contentSha256(sealPayload);
    String signature = jwtService.signEvidence(Map.of(
        "contentSha256", contentSha256,
        "systemId", system.id().toString(),
        "generatedAt", generatedAt.toString(),
        "evidencePackVersion", PACK_VERSION,
        "auditChainHead", auditChainHead == null ? "" : auditChainHead));

    Map<String, Object> auditPayload = new LinkedHashMap<>();
    auditPayload.put("decision", releaseGate.decision().name());
    auditPayload.put("contentSha256", contentSha256);
    auditPayload.put("signature", signature);
    auditPayload.put("format", exportFormat == null ? "json" : exportFormat);
    auditPayload.put("evidencePackVersion", PACK_VERSION);
    auditService.append(
        system.id(),
        "evidence_pack.exported",
        "ai_system",
        system.id().toString(),
        auditPayload);

    return new EvidencePackResponse(
        system.id(),
        generatedAt,
        releaseGate.decision(),
        riskClassification,
        evidence,
        evalRuns,
        dataContracts,
        approvals,
        auditEvents,
        determination,
        Map.of(),
        evgraphArtifacts,
        PACK_VERSION,
        contentSha256,
        generator,
        auditChainHead,
        corpusVersion,
        acceptedLinks,
        queue,
        currentGaps,
        acceptedArtifacts,
        evidenceCounts,
        monitoringPlan,
        signature,
        frameworks);
  }

  /** Crosswalk views the tenant's plan includes, or null so the pack omits the key (and the seal covers none). */
  private Map<String, Object> frameworks(UUID systemId) {
    Map<String, Object> frameworks = new java.util.TreeMap<>();
    CrosswalkService.FEATURES.forEach((framework, feature) -> {
      if (crosswalks.find(framework).isPresent() && entitlements.hasFeature(feature)) {
        frameworks.put(framework, crosswalks.view(framework, systemId));
      }
    });
    return frameworks.isEmpty() ? null : frameworks;
  }

  public Map<String, Object> evgraphArtifactFiles(UUID systemId) {
    AiSystem system = repository.findById(systemId)
        .orElseThrow(() -> new IllegalArgumentException("AI system not found"));
    List<Map<String, Object>> approvals = approvalWorkflowService.listBySystemId(system.id()).stream()
        .map(this::workflowEvidence)
        .toList();
    return evgraphArtifacts(system, approvals);
  }

  public byte[] renderPdf(EvidencePackResponse pack, String systemName) {
    return EvidencePackPdfRenderer.render(pack, systemName);
  }

  public String pdfFilename(EvidencePackResponse pack) {
    return EvidencePackPdfRenderer.filename(pack.systemId(), pack.generatedAt());
  }

  /**
   * JSON trio Evgraph's ModelCardAdapter already understands. Run:
   * {@code evgraph scan-promotion --model-card ... --approval ... --deployment ... --gate}
   */
  private Map<String, Object> evgraphArtifacts(AiSystem system, List<Map<String, Object>> approvals) {
    String publicClaimsSlug = PublicClaimsCatalog.slugFromDataSources(system.dataSources());
    if (publicClaimsSlug != null) {
      return publicClaimsService.evgraphArtifacts(publicClaimsSlug);
    }
    Map<String, Object> modelCard = new LinkedHashMap<>();
    String modelName = system.modelName() == null || system.modelName().isBlank()
        ? system.name()
        : system.modelName();
    modelCard.put("model_name", modelName);
    modelCard.put("intended_use", system.purpose() == null ? "" : system.purpose());

    // Only an approved stage names an approver; without one the field stays empty (evgraph needs the key).
    Map<String, Object> approval = new LinkedHashMap<>();
    approval.put("approver", "");
    for (Map<String, Object> workflow : approvals) {
      Object stages = workflow.get("stages");
      if (!(stages instanceof List<?> list)) {
        continue;
      }
      for (Object stageObj : list) {
        if (!(stageObj instanceof Map<?, ?> stage)) {
          continue;
        }
        if (!"APPROVED".equals(String.valueOf(stage.get("status")))) {
          continue;
        }
        Object actor = stage.get("actorId");
        if (actor != null && !actor.toString().isBlank()) {
          approval.put("approver", actor.toString());
        }
        Object actedAt = stage.get("actedAt");
        if (actedAt != null && !actedAt.toString().isBlank()) {
          approval.put("approved_at", actedAt.toString());
        }
      }
    }

    // Devernance records no deployments, so there is no deployment time to state. A record edit is not a
    // deployment: using updatedAt let any rename make "approval precedes deployment" pass.
    Map<String, Object> deployment = new LinkedHashMap<>();

    Map<String, Object> root = new LinkedHashMap<>();
    root.put(
        "howto",
        "Write these three JSON objects to files and run: evgraph scan-promotion "
            + "--model-card model_card.json --approval approval.json --deployment deployment.json "
            + "--format markdown --gate");
    root.put("library", "evgraph (PyPI). Not bundled in this API.");
    root.put("model_card", modelCard);
    root.put("approval", approval);
    root.put("deployment", deployment);
    String datasetManifest = datasetManifestCsv(system.dataSources());
    if (datasetManifest != null) {
      root.put("dataset_manifest_csv", datasetManifest);
    }
    return root;
  }

  /**
   * The system's declared data sources as an evgraph dataset manifest, or null when it declares none. Devernance
   * stores no license or personal-data flag for a source, so both cells stay empty rather than being guessed.
   */
  static String datasetManifestCsv(List<String> dataSources) {
    java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
    for (String source : dataSources == null ? List.<String>of() : dataSources) {
      if (source != null && !source.isBlank()) {
        ids.add(source.strip().replaceAll("[\\r\\n]+", " "));
      }
    }
    if (ids.isEmpty()) {
      return null;
    }
    StringBuilder csv = new StringBuilder("dataset_id,source,license,contains_pii\n");
    for (String id : ids) {
      csv.append(csvCell(id)).append(",ai_system.dataSources,,\n");
    }
    return csv.toString();
  }

  private static String csvCell(String value) {
    if (value.contains(",") || value.contains("\"")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private Map<String, Object> acceptedArtifacts(Map<String, Object> evgraphArtifacts) {
    Map<String, Object> files = new LinkedHashMap<>();
    files.put("model_card", evgraphArtifacts.get("model_card"));
    files.put("approval", evgraphArtifacts.get("approval"));
    files.put("deployment", evgraphArtifacts.get("deployment"));
    Object datasetManifest = evgraphArtifacts.get("dataset_manifest_csv");
    if (datasetManifest != null) {
      files.put("dataset_manifest_csv", datasetManifest);
    }
    return files;
  }

  private Map<String, Object> linkRow(MappingProposalService.ProposalView row) {
    Map<String, Object> link = new LinkedHashMap<>();
    link.put("id", row.id().toString());
    link.put("status", row.status());
    link.put("relation", row.relation());
    link.put("provisionKey", row.provisionKey());
    link.put("forceStatus", row.forceStatus());
    link.put("forceFrom", row.forceFrom() == null ? null : row.forceFrom().toString());
    return link;
  }

  private Map<String, Object> queueRow(MappingProposalService.ProposalView row) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", row.id().toString());
    item.put("status", row.status());
    item.put("relation", row.relation());
    item.put("label", "abstain".equals(row.relation()) ? "abstain" : row.status());
    return item;
  }

  private Map<String, Object> riskClassification(AiSystem system) {
    Map<String, Object> risk = new LinkedHashMap<>();
    risk.put("riskClass", system.riskClass());
    risk.put("basis", system.riskBasis());
    risk.put("deploymentRegion", system.deploymentRegion());
    risk.put("vendorName", system.vendorName() == null ? "" : system.vendorName());
    risk.put("modelName", system.modelName() == null ? "" : system.modelName());
    risk.put("modelVersion", system.modelVersion() == null ? "" : system.modelVersion());
    risk.put("dataSources", system.dataSources());
    risk.put("sector", system.sector() == null ? "" : system.sector());
    risk.put("decisionImpact", system.decisionImpact() == null ? "" : system.decisionImpact());
    risk.put("affectedUsers", system.affectedUsers());
    return risk;
  }

  private Map<String, Object> workflowEvidence(ApprovalWorkflow workflow) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("workflowId", workflow.id());
    evidence.put("trigger", workflow.trigger());
    evidence.put("status", workflow.status());
    evidence.put("openedAt", workflow.openedAt());
    evidence.put("closedAt", workflow.closedAt());
    evidence.put("stages", workflow.stages().stream().map(this::stageEvidence).toList());
    return evidence;
  }

  private Map<String, Object> stageEvidence(ApprovalStage stage) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("stageType", stage.stageType());
    evidence.put("status", stage.status());
    evidence.put("actorId", stage.actorId());
    evidence.put("rationale", stage.rationale());
    evidence.put("actedAt", stage.actedAt());
    return evidence;
  }

  private Map<String, Object> contractEvidence(AiSystem system, DataContract contract) {
    List<DriftEvent> driftEvents = dataContractService.listDriftEvents(contract.id());
    List<Map<String, Object>> drift = driftEvents.stream()
        .map(this::driftEvidence)
        .toList();
    List<UUID> openDriftEventIds = driftEvents.stream()
        .filter(event -> event.status() == DriftStatus.OPEN)
        .map(DriftEvent::id)
        .toList();
    Map<String, Object> lineage = new LinkedHashMap<>();
    lineage.put("systemId", system.id());
    lineage.put("systemName", system.name());
    lineage.put("contractId", contract.id());
    lineage.put("contractName", contract.name());
    lineage.put("owner", contract.owner());
    lineage.put("version", contract.version());
    lineage.put("openDriftEventIds", openDriftEventIds);

    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("id", contract.id());
    evidence.put("name", contract.name());
    evidence.put("owner", contract.owner());
    evidence.put("version", contract.version());
    evidence.put("status", contract.status());
    evidence.put("coverage", contract.coverage());
    evidence.put("lineage", lineage);
    evidence.put("driftEvents", drift);
    return evidence;
  }

  private Map<String, Object> driftEvidence(DriftEvent event) {
    Map<String, Object> drift = new LinkedHashMap<>();
    drift.put("id", event.id());
    drift.put("severity", event.severity());
    drift.put("field", event.field());
    drift.put("description", event.description());
    drift.put("status", event.status());
    drift.put("createdAt", event.createdAt());
    drift.put("updatedAt", event.updatedAt());
    return drift;
  }

  static Map<String, Object> documentRow(EvidenceDocument document) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("documentId", document.id().toString());
    row.put("type", document.type());
    row.put("title", document.title());
    row.put("sha256", document.checksum());
    row.put("ingestionStatus", document.ingestionStatus());
    row.put("indexedAt", document.createdAt() == null ? null : document.createdAt().toString());
    return row;
  }

  static Map<String, Object> evalRunRow(EvalRun run) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("runId", run.runId().toString());
    row.put("dataset", run.dataset());
    row.put("modelVersion", run.modelVersion());
    row.put("promptVersion", run.promptVersion());
    row.put("threshold", run.threshold());
    row.put("metrics", run.metrics() == null ? Map.of() : run.metrics());
    row.put("runDecision", run.releaseDecision() == null ? null : run.releaseDecision().name());
    row.put("synthetic", run.metrics() != null && Boolean.TRUE.equals(run.metrics().get("synthetic")));
    row.put("completedAt", run.completedAt() == null ? null : run.completedAt().toString());
    return row;
  }
}
