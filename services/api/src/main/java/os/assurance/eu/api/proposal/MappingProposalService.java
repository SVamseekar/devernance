package os.assurance.eu.api.proposal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.corpus.CorpusQueryService;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MappingProposalService {
  private final MappingProposalJpaRepository proposals;
  private final AiSystemRepository systems;
  private final CorpusQueryService corpus;
  private final AuditService auditService;
  private final TenantContext tenantContext;
  private final RulesDocumentMapper rulesMapper;
  private final SlmMapClient slm;
  private final TransactionTemplate transaction;

  public MappingProposalService(
      MappingProposalJpaRepository proposals,
      AiSystemRepository systems,
      CorpusQueryService corpus,
      AuditService auditService,
      TenantContext tenantContext,
      SlmMapClient slm,
      PlatformTransactionManager transactionManager) {
    this.proposals = proposals;
    this.systems = systems;
    this.corpus = corpus;
    this.auditService = auditService;
    this.tenantContext = tenantContext;
    this.rulesMapper = new RulesDocumentMapper();
    this.slm = slm;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Transactional(readOnly = true)
  public ProposalListResponse list(UUID systemId) {
    requireSystem(systemId);
    String current = corpus.currentVersionHash();
    List<ProposalView> items = proposals
        .findAllByTenantIdAndSystemIdOrderByCreatedAtAsc(tenantContext.tenantId(), systemId)
        .stream()
        .map(row -> toView(row, current))
        .toList();
    return new ProposalListResponse(current, items, slm.down());
  }

  @Transactional
  public ProposalView create(UUID systemId, CreateMappingProposalRequest request) {
    requireSystem(systemId);
    if (request.provisionKey() != null && !request.provisionKey().isBlank()
        && !corpus.currentCorpusContains(request.provisionKey())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provisionKey is not in the pinned corpus");
    }
    String current = corpus.currentVersionHash();
    String pinned = request.corpusVersion() == null || request.corpusVersion().isBlank()
        ? current
        : request.corpusVersion();
    MappingProposalEntity saved = proposals.save(new MappingProposalEntity(
        UUID.randomUUID(),
        tenantContext.tenantId(),
        systemId,
        "PENDING",
        request.relation(),
        pinned,
        blankToNull(request.adapterVersion()),
        blankToNull(request.provisionKey()),
        blankToNull(request.excerpt()),
        Instant.now()));
    audit(saved, "mapping_proposal.created", Map.of(
        "relation", saved.relation(),
        "provisionKey", String.valueOf(saved.provisionKey())));
    return toView(saved, current);
  }

  /**
   * The model calls run before the transaction opens, so a slow or unreachable model never holds a
   * database connection. After the first failed call the rest of the batch goes to the rules mapper.
   */
  public List<ProposalView> mapDocuments(UUID systemId, List<MapDocumentRequest> documents) {
    requireSystem(systemId);
    List<LawProvision> lawIndex = lawIndex();
    List<String> candidateIds = lawIndex.stream().map(LawProvision::provisionKey).toList();
    List<MappedDocument> mapped = new ArrayList<>();
    boolean slmAvailable = true;
    for (MapDocumentRequest document : documents) {
      SlmMapClient.Outcome outcome = slmAvailable
          ? slm.map(document.title(), document.text(), candidateIds)
          : SlmMapClient.Outcome.down();
      if (outcome.kind() == SlmMapClient.Kind.DOWN) {
        slmAvailable = false;
      }
      if (outcome.kind() == SlmMapClient.Kind.INVALID) {
        mapped.add(new MappedDocument(document, null, null));
      } else if (outcome.kind() == SlmMapClient.Kind.ABSTAIN) {
        mapped.add(new MappedDocument(document, MappingDraft.abstain(excerpt(document.text())), SlmMapClient.ADAPTER_VERSION));
      } else if (outcome.kind() == SlmMapClient.Kind.CANDIDATE) {
        mapped.add(new MappedDocument(document, citeCandidate(outcome, lawIndex, document.text()), SlmMapClient.ADAPTER_VERSION));
      } else {
        mapped.add(new MappedDocument(document, rulesMapper.map(document.title(), document.text(), lawIndex), null));
      }
    }
    return transaction.execute(status -> persistMapped(systemId, mapped));
  }

  private List<ProposalView> persistMapped(UUID systemId, List<MappedDocument> mapped) {
    requireSystem(systemId);
    String current = corpus.currentVersionHash();
    List<ProposalView> created = new ArrayList<>();
    for (MappedDocument item : mapped) {
      if (item.draft() == null) {
        auditSchemaInvalid(systemId);
        continue;
      }
      MappingDraft draft = item.draft();
      MappingProposalEntity saved = proposals.save(new MappingProposalEntity(
          UUID.randomUUID(),
          tenantContext.tenantId(),
          systemId,
          "PENDING",
          draft.relation(),
          current,
          item.adapterVersion(),
          draft.provisionKey(),
          draft.excerpt(),
          Instant.now()));
      audit(saved, "mapping_proposal.mapped", Map.of(
          "documentTitle", item.document().title(),
          "relation", draft.relation()));
      created.add(toView(saved, current));
    }
    return created;
  }

  /** A null draft means the model answered with something outside the schema. */
  private record MappedDocument(MapDocumentRequest document, MappingDraft draft, String adapterVersion) {
  }

  @Transactional
  public ProposalView accept(UUID systemId, UUID proposalId) {
    return decide(systemId, proposalId, "ACCEPTED");
  }

  @Transactional
  public ProposalView reject(UUID systemId, UUID proposalId) {
    MappingProposalEntity proposal = pending(systemId, proposalId);
    return finish(proposal, "REJECTED", "mapping_proposal.rejected");
  }

  private ProposalView decide(UUID systemId, UUID proposalId, String status) {
    MappingProposalEntity proposal = pending(systemId, proposalId);
    String current = corpus.currentVersionHash();
    if ("abstain".equals(proposal.relation())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Abstain has no link to accept");
    }
    if (!current.equals(proposal.corpusVersion())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Corpus version does not match the pinned corpus");
    }
    if (proposal.provisionKey() != null && !corpus.currentCorpusContains(proposal.provisionKey())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Provision is not in the pinned corpus");
    }
    finish(proposal, status, "mapping_proposal.accepted");
    String force = forceStatus(proposal.provisionKey());
    proposal.assignMode("FUTURE".equals(force) ? "INFORMATIONAL" : "WARNING");
    proposals.save(proposal);
    return toView(proposal, corpus.currentVersionHash());
  }

  private ProposalView finish(MappingProposalEntity proposal, String status, String eventType) {
    proposal.decide(status, tenantContext.actorId(), Instant.now());
    proposals.save(proposal);
    String current = corpus.currentVersionHash();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("corpusVersion", proposal.corpusVersion());
    payload.put("currentCorpusVersion", current);
    payload.put("relation", proposal.relation());
    payload.put("provisionKey", proposal.provisionKey());
    payload.put("decision", status);
    auditService.append(
        proposal.systemId(),
        eventType,
        "mapping_proposal",
        proposal.id().toString(),
        payload);
    return toView(proposal, current);
  }

  @Transactional
  public ProposalView setMode(UUID systemId, UUID proposalId, String mode) {
    requireSystem(systemId);
    MappingProposalEntity proposal = proposals
        .findByIdAndTenantIdAndSystemId(proposalId, tenantContext.tenantId(), systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
    if (!"ACCEPTED".equals(proposal.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an accepted link has a control mode");
    }
    String normalized = mode == null ? "" : mode.trim().toUpperCase();
    if (!List.of("INFORMATIONAL", "WARNING", "APPROVAL_REQUIRED", "BLOCKING").contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown control mode");
    }
    String force = forceStatus(proposal.provisionKey());
    if ("FUTURE".equals(force) && "BLOCKING".equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A future duty cannot be blocking");
    }
    if ("FUTURE".equals(force)) {
      normalized = "INFORMATIONAL";
    }
    String previous = proposal.controlMode();
    proposal.assignMode(normalized);
    proposals.save(proposal);
    audit(proposal, "mapping_proposal.mode_changed", Map.of(
        "from", String.valueOf(previous),
        "to", normalized));
    return toView(proposal, corpus.currentVersionHash());
  }

  @Transactional
  public void reopenAcceptedLinks(UUID systemId) {
    Instant now = Instant.now();
    for (MappingProposalEntity row : proposals.findAllByTenantIdAndSystemIdOrderByCreatedAtAsc(
        tenantContext.tenantId(), systemId)) {
      if ("ACCEPTED".equals(row.status()) && !"abstain".equals(row.relation())) {
        row.reopen(now);
        proposals.save(row);
        audit(row, "mapping_proposal.reopened", Map.of("reason", "system_change_scope"));
      }
    }
  }

  private MappingProposalEntity pending(UUID systemId, UUID proposalId) {
    requireSystem(systemId);
    MappingProposalEntity proposal = proposals
        .findByIdAndTenantIdAndSystemId(proposalId, tenantContext.tenantId(), systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
    if (!"PENDING".equals(proposal.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal is already decided");
    }
    return proposal;
  }

  private void requireSystem(UUID systemId) {
    if (systems.findById(systemId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found");
    }
  }

  private ProposalView toView(MappingProposalEntity row, String current) {
    return new ProposalView(
        row.id(),
        row.systemId(),
        row.status(),
        row.relation(),
        displayState(row, current),
        row.corpusVersion(),
        row.corpusVersion(),
        current,
        row.provisionKey(),
        row.excerpt(),
        row.adapterVersion(),
        forceStatus(row.provisionKey()),
        forceFrom(row.provisionKey()));
  }

  private List<LawProvision> lawIndex() {
    return corpus.current().provisions().stream()
        .map(row -> new LawProvision(row.provisionKey(), row.textExcerpt(), row.forceStatus(), row.forceFrom()))
        .toList();
  }

  private String forceStatus(String provisionKey) {
    CorpusQueryService.ProvisionView match = provision(provisionKey);
    return match == null ? null : match.forceStatus();
  }

  private LocalDate forceFrom(String provisionKey) {
    CorpusQueryService.ProvisionView match = provision(provisionKey);
    return match == null ? null : match.forceFrom();
  }

  private void audit(MappingProposalEntity proposal, String eventType, Map<String, Object> details) {
    Map<String, Object> payload = new LinkedHashMap<>(details);
    payload.put("proposalId", proposal.id().toString());
    payload.put("corpusVersion", proposal.corpusVersion());
    auditService.append(proposal.systemId(), eventType, "mapping_proposal", proposal.id().toString(), payload);
  }

  private CorpusQueryService.ProvisionView provision(String provisionKey) {
    return corpus.provision(provisionKey).orElse(null);
  }

  static String displayState(MappingProposalEntity row, String current) {
    if ("abstain".equals(row.relation())) {
      return "abstain";
    }
    if (current == null || !current.equals(row.corpusVersion())) {
      return "corpus_mismatch";
    }
    if ("PENDING".equals(row.status())) {
      return "pending";
    }
    return row.status().toLowerCase();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record ProposalListResponse(String currentCorpusVersion, List<ProposalView> items, boolean slmDown) {
  }

  private void auditSchemaInvalid(UUID systemId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reason", "invalid_json");
    auditService.append(systemId, "map.schema_invalid", "mapping_proposal", systemId.toString(), payload);
  }

  private MappingDraft citeCandidate(SlmMapClient.Outcome outcome, List<LawProvision> lawIndex, String text) {
    for (LawProvision provision : lawIndex) {
      if (provision.provisionKey().equals(outcome.obligationId())) {
        return new MappingDraft(
            outcome.relation(),
            provision.provisionKey(),
            provision.forceStatus(),
            provision.forceFrom(),
            excerpt(text));
      }
    }
    return MappingDraft.abstain(excerpt(text));
  }

  private static String excerpt(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String trimmed = text.strip();
    return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
  }

  public record ProposalView(
      UUID id,
      UUID systemId,
      String status,
      String relation,
      String displayState,
      String corpusVersion,
      String pinnedCorpusVersion,
      String currentCorpusVersion,
      String provisionKey,
      String excerpt,
      String adapterVersion,
      String forceStatus,
      LocalDate forceFrom) {
  }

  public record MapDocumentRequest(String title, String text) {
  }
}
