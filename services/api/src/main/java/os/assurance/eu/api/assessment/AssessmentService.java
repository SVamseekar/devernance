package os.assurance.eu.api.assessment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.corpus.CorpusQueryService;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.proposal.MappingProposalEntity;
import os.assurance.eu.api.proposal.MappingProposalJpaRepository;
import os.assurance.eu.api.readiness.CertificationReadinessProperties;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssessmentService {
  private final MappingProposalJpaRepository proposals;
  private final AssessmentApplicabilityJpaRepository applicability;
  private final EvidenceExceptionJpaRepository exceptions;
  private final AiSystemRepository systems;
  private final EvidenceRepository evidence;
  private final CorpusQueryService corpus;
  private final UserJpaRepository users;
  private final TenantContext tenantContext;
  private final AuditService auditService;
  private final CertificationReadinessProperties readiness;
  private final Clock clock;

  public AssessmentService(
      MappingProposalJpaRepository proposals,
      AssessmentApplicabilityJpaRepository applicability,
      EvidenceExceptionJpaRepository exceptions,
      AiSystemRepository systems,
      EvidenceRepository evidence,
      CorpusQueryService corpus,
      UserJpaRepository users,
      TenantContext tenantContext,
      AuditService auditService,
      CertificationReadinessProperties readiness,
      Clock clock) {
    this.proposals = proposals;
    this.applicability = applicability;
    this.exceptions = exceptions;
    this.systems = systems;
    this.evidence = evidence;
    this.corpus = corpus;
    this.users = users;
    this.tenantContext = tenantContext;
    this.auditService = auditService;
    this.readiness = readiness;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AssessmentResponse assess(UUID systemId) {
    AiSystem system = requireSystem(systemId);
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    boolean evidenceInsideWindow = evidenceInsideExistingWindow(system);
    boolean requiredTest = system.evalScore() >= readiness.getEvalPassThreshold();
    List<AssessmentItem> items = proposals
        .findAllByTenantIdAndSystemIdOrderByCreatedAtAsc(tenantContext.tenantId(), systemId)
        .stream()
        .map(row -> item(system, row, today, evidenceInsideWindow, requiredTest))
        .toList();
    return new AssessmentResponse(corpus.currentVersionHash(), counts(items), items);
  }

  @Transactional(readOnly = true)
  public Map<String, Integer> counts(UUID systemId) {
    return assess(systemId).counts();
  }

  @Transactional
  public AssessmentItem setApplicability(UUID systemId, UUID proposalId, String applicabilityValue) {
    requireSystem(systemId);
    MappingProposalEntity proposal = requireProposal(systemId, proposalId);
    String normalized = applicabilityValue == null ? "" : applicabilityValue.trim().toUpperCase();
    if (!List.of("APPLICABLE", "UNCERTAIN", "NOT_APPLICABLE").contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown applicability");
    }
    UUID reviewer = "NOT_APPLICABLE".equals(normalized) ? tenantContext.actorId() : null;
    AssessmentApplicabilityEntity existing = applicability
        .findByTenantIdAndSystemIdAndProposalId(tenantContext.tenantId(), systemId, proposalId)
        .orElse(null);
    String previous = existing == null ? "APPLICABLE" : existing.applicability();
    AssessmentApplicabilityEntity row = existing == null
        ? new AssessmentApplicabilityEntity(
            UUID.randomUUID(),
            tenantContext.tenantId(),
            systemId,
            proposalId,
            normalized,
            reviewer)
        : existing;
    row.update(normalized, reviewer);
    applicability.save(row);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("from", previous);
    payload.put("to", normalized);
    payload.put("reviewerId", reviewer == null ? "" : reviewer.toString());
    payload.put("proposalId", proposalId.toString());
    auditService.append(systemId, "assessment.applicability_set", "assessment", proposalId.toString(), payload);
    return itemFor(systemId, proposal);
  }

  @Transactional
  public AssessmentItem recordException(UUID systemId, UUID proposalId, String rationale, LocalDate expiresOn) {
    requireSystem(systemId);
    requireProposal(systemId, proposalId);
    if (rationale == null || rationale.isBlank() || expiresOn == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception needs a rationale and an expiry");
    }
    // Same UTC calendar as assess(), so "expired" and "accepted" agree whatever the server's zone.
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    if (!expiresOn.isAfter(today)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception expiry must be after today (UTC)");
    }
    if (expiresOn.isAfter(today.plusYears(1))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exceptions may last at most one year");
    }
    exceptions.save(new EvidenceExceptionEntity(
        UUID.randomUUID(),
        tenantContext.tenantId(),
        systemId,
        proposalId,
        rationale.trim(),
        expiresOn,
        tenantContext.actorId(),
        Instant.now(clock)));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("expiresOn", expiresOn.toString());
    payload.put("proposalId", proposalId.toString());
    auditService.append(systemId, "assessment.exception_recorded", "assessment", proposalId.toString(), payload);
    return itemFor(systemId, requireProposal(systemId, proposalId));
  }

  @Transactional(readOnly = true)
  public AssessmentItem refuseSatisfied(UUID systemId, UUID proposalId) {
    AssessmentItem item = itemFor(systemId, requireProposal(systemId, proposalId));
    if ("NOT_APPLICABLE".equals(item.applicability())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Not applicable cannot become satisfied");
    }
    return item;
  }

  private AssessmentItem itemFor(UUID systemId, MappingProposalEntity proposal) {
    AiSystem system = requireSystem(systemId);
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    return item(
        system,
        proposal,
        today,
        evidenceInsideExistingWindow(system),
        system.evalScore() >= readiness.getEvalPassThreshold());
  }

  private AssessmentItem item(
      AiSystem system,
      MappingProposalEntity proposal,
      LocalDate today,
      boolean evidenceInsideWindow,
      boolean requiredTest) {
    AssessmentApplicabilityEntity stored = applicability
        .findByTenantIdAndSystemIdAndProposalId(tenantContext.tenantId(), system.id(), proposal.id())
        .orElse(null);
    String applicabilityValue = stored == null ? "APPLICABLE" : stored.applicability();
    String reviewer = stored == null || stored.reviewerId() == null ? null : stored.reviewerId().toString();
    EvidenceExceptionEntity exception = exceptions
        .findAllByTenantIdAndSystemIdAndProposalIdOrderByCreatedAtDesc(
            tenantContext.tenantId(), system.id(), proposal.id())
        .stream()
        .findFirst()
        .orElse(null);
    CorpusQueryService.ProvisionView provision = provision(proposal.provisionKey());
    String force = provision == null ? null : provision.forceStatus();
    boolean scopeMatches = provision == null || provision.scopeNote() == null || provision.scopeNote().isBlank();
    EvidenceStatus derived = EvidenceStatusRules.derive(
        new EvidenceFacts(
            proposal.status(),
            proposal.relation(),
            force,
            applicabilityValue,
            reviewer,
            exception == null ? null : exception.rationale(),
            exception == null ? null : exception.expiresOn(),
            evidenceInsideWindow,
            requiredTest,
            scopeMatches),
        today);
    return new AssessmentItem(
        proposal.id(),
        applicabilityValue,
        force,
        proposal.status(),
        proposal.relation(),
        derived == null ? null : derived.name(),
        reviewer);
  }

  private boolean evidenceInsideExistingWindow(AiSystem system) {
    boolean indexed = evidence.findDocumentsBySystemId(system.id()).stream()
        .anyMatch(document -> "indexed".equalsIgnoreCase(document.ingestionStatus()));
    return indexed && system.evidenceCoverage() >= readiness.getEvidencePassThreshold();
  }

  private Map<String, Integer> counts(List<AssessmentItem> items) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (EvidenceStatus status : EvidenceStatus.values()) {
      counts.put(countKey(status), 0);
    }
    for (AssessmentItem item : items) {
      if (item.derivedStatus() == null) {
        continue;
      }
      EvidenceStatus status = EvidenceStatus.valueOf(item.derivedStatus());
      if (!EvidenceStatusRules.countsAsCurrent(status)) {
        continue;
      }
      String key = countKey(status);
      counts.put(key, counts.get(key) + 1);
    }
    return counts;
  }

  private static String countKey(EvidenceStatus status) {
    return switch (status) {
      case SATISFIED -> "satisfied";
      case INSUFFICIENT -> "insufficient";
      case MISSING -> "missing";
      case NEEDS_HUMAN_REVIEW -> "needsHumanReview";
      case NOT_APPLICABLE -> "notApplicable";
      case ACCEPTED_EXCEPTION -> "acceptedException";
    };
  }

  private CorpusQueryService.ProvisionView provision(String provisionKey) {
    return corpus.provision(provisionKey).orElse(null);
  }

  private AiSystem requireSystem(UUID systemId) {
    return systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
  }

  private MappingProposalEntity requireProposal(UUID systemId, UUID proposalId) {
    return proposals.findByIdAndTenantIdAndSystemId(proposalId, tenantContext.tenantId(), systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
  }

  public record AssessmentResponse(String corpusVersion, Map<String, Integer> counts, List<AssessmentItem> items) {
  }

  public record AssessmentItem(
      UUID proposalId,
      String applicability,
      String forceStatus,
      String linkStatus,
      String relation,
      String derivedStatus,
      String reviewerId) {
  }
}
