package os.assurance.eu.api.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.demo.DemoProperties;
import os.assurance.eu.api.evidence.EvidenceChunker.ChunkDraft;
import os.assurance.eu.api.evidence.PromptInjectionGuard.SanitizedText;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.GateRecalculator;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EvidenceService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EvidenceService.class);
  private final EvidenceRepository repository;
  private final EvidenceQueryJpaRepository queries;
  private final TextExtractionService textExtractionService;
  private final PromptInjectionGuard promptInjectionGuard;
  private final EvidenceIngestionGuard evidenceGuard;
  private final EvidenceChunker chunker;
  private final EvidenceEmbeddingService embeddingService;
  private final AuditService auditService;
  private final TenantContext tenantContext;
  private final EvidenceProperties properties;
  private final GateRecalculator gateRecalculator;
  private final TransactionTemplate transaction;
  private final EvidenceRetriever retriever;

  public EvidenceService(
      EvidenceRepository repository,
      EvidenceQueryJpaRepository queries,
      TextExtractionService textExtractionService,
      PromptInjectionGuard promptInjectionGuard,
      EvidenceIngestionGuard evidenceGuard,
      EvidenceChunker chunker,
      EvidenceEmbeddingService embeddingService,
      AuditService auditService,
      TenantContext tenantContext,
      EvidenceProperties properties,
      GateRecalculator gateRecalculator,
      PlatformTransactionManager transactionManager,
      EvidenceRetriever retriever) {
    this.repository = repository;
    this.queries = queries;
    this.textExtractionService = textExtractionService;
    this.promptInjectionGuard = promptInjectionGuard;
    this.evidenceGuard = evidenceGuard;
    this.chunker = chunker;
    this.embeddingService = embeddingService;
    this.auditService = auditService;
    this.tenantContext = tenantContext;
    this.properties = properties;
    this.gateRecalculator = gateRecalculator;
    this.transaction = new TransactionTemplate(transactionManager);
    this.retriever = retriever;
  }

  /**
   * Fetching, parsing and embedding run before the transaction opens: a slow source URL or a long document must
   * not hold a database connection. Only the inserts, the audit entry and the gate recalculation are transactional.
   */
  public EvidenceDocumentResponse ingest(CreateEvidenceDocumentRequest request) {
    evidenceGuard.validateDocumentRequest(request);
    String extracted = textExtractionService.extract(request);
    // Text past the limit is not indexed; the document is marked so nobody mistakes it for fully searchable.
    boolean truncated = extracted.length() >= properties.maxContentCharacters();
    if (extracted.length() > properties.maxContentCharacters()) {
      extracted = extracted.substring(0, properties.maxContentCharacters());
    }
    SanitizedText sanitized = promptInjectionGuard.sanitizeDocumentText(extracted);
    List<ChunkDraft> chunkDrafts = chunker.chunk(sanitized.text());
    long embedStarted = System.nanoTime();
    // The title travels with each chunk into the embedding, so "the DPIA says ..." finds the DPIA's chunks.
    List<String> embeddings = embeddingService.embedAll(chunkDrafts.stream()
        .map(draft -> request.title() + "\n" + draft.content())
        .toList());
    log.info("Embedded {} chunks of a {}-character document in {} ms", chunkDrafts.size(), extracted.length(),
        (System.nanoTime() - embedStarted) / 1_000_000);
    Map<String, Object> metadata = evidenceGuard.sanitizeMetadata(request.metadata());
    return transaction.execute(status -> persist(request, sanitized, truncated, chunkDrafts, embeddings, metadata));
  }

  private EvidenceDocumentResponse persist(
      CreateEvidenceDocumentRequest request,
      SanitizedText sanitized,
      boolean truncated,
      List<ChunkDraft> chunkDrafts,
      List<String> embeddings,
      Map<String, Object> metadata) {
    Instant now = Instant.now();
    UUID documentId = UUID.randomUUID();
    String checksum = request.checksum() == null || request.checksum().isBlank()
        ? checksum(sanitized.text())
        : request.checksum();
    EvidenceDocument saved = repository.saveDocument(new EvidenceDocument(
        documentId,
        request.systemId(),
        request.type(),
        request.title(),
        request.sourceUri(),
        checksum,
        chunkDrafts.size(),
        sanitized.removedLines().isEmpty() && !truncated ? "indexed" : "indexed_with_warnings",
        now));
    repository.saveChunks(toChunks(saved, chunkDrafts, embeddings, metadata, sanitized.removedLines(), truncated));
    auditService.append(
        saved.systemId(),
        "evidence.document_indexed",
        "evidence_document",
        saved.id().toString(),
        Map.of(
            "title", saved.title(),
            "type", saved.type(),
            "chunkCount", saved.chunkCount(),
            "embeddingProvider", embeddingService.providerName(),
            "removedPromptInjectionLines", sanitized.removedLines().size(),
            "truncatedAtCharacters", truncated ? properties.maxContentCharacters() : 0));
    gateRecalculator.recalculate(saved.systemId());
    return saved.toResponse();
  }

  @Transactional(readOnly = true)
  public List<EvidenceDocumentResponse> listDocuments(UUID systemId) {
    return repository.findDocumentsBySystemId(systemId).stream()
        .map(EvidenceDocument::toResponse)
        .toList();
  }

  @Transactional
  public EvidenceQueryResponse answer(AiSystem system, String question) {
    String sanitizedQuestion = evidenceGuard.validateQuestion(promptInjectionGuard.sanitizeQuestion(question));
    List<EvidenceRetriever.Passage> passages =
        retriever.retrieve(repository.findDocumentsBySystemId(system.id()), sanitizedQuestion);

    EvidenceQueryResponse response = passages.isEmpty()
        ? fallbackAnswer(system)
        : citedAnswer(system, sanitizedQuestion, passages);
    queries.save(new EvidenceQueryEntity(
        UUID.randomUUID(),
        tenantContext.tenantId(),
        system.id(),
        sanitizedQuestion,
        response.answer(),
        response.confidence(),
        response.citations(),
        tenantContext.actorId(),
        Instant.now()));
    // Demo questions are visible to every visitor and deleted after an hour (DemoQuestionCleanupJob); the
    // append-only audit trail cannot forget, so it records only the length of a demo visitor's text.
    boolean demo = DemoProperties.DEMO_TENANT_ID.equals(tenantContext.tenantId());
    auditService.append(
        system.id(),
        "evidence.query_answered",
        "ai_system",
        system.id().toString(),
        Map.of(
            demo ? "questionLength" : "question", demo ? sanitizedQuestion.length() : sanitizedQuestion,
            "confidence", response.confidence(),
            "citationCount", response.citations().size(),
            "search", retriever.searchDescription()));
    return response;
  }

  private List<EvidenceChunk> toChunks(
      EvidenceDocument document,
      List<ChunkDraft> chunkDrafts,
      List<String> embeddings,
      Map<String, Object> requestMetadata,
      List<String> removedLines,
      boolean truncated) {
    Map<String, Object> metadata = requestMetadata == null ? Map.of() : requestMetadata;
    return java.util.stream.IntStream.range(0, chunkDrafts.size())
        .mapToObj(index -> {
          ChunkDraft draft = chunkDrafts.get(index);
          return new EvidenceChunk(
              UUID.randomUUID(),
              document.id(),
              index,
              draft.sectionRef(),
              draft.content(),
              checksum(draft.content()),
              embeddings.get(index),
              embeddingService.providerName(),
              metadataWithGuard(metadata, removedLines, truncated));
        })
        .toList();
  }

  private Map<String, Object> metadataWithGuard(
      Map<String, Object> metadata, List<String> removedLines, boolean truncated) {
    java.util.LinkedHashMap<String, Object> guarded = new java.util.LinkedHashMap<>(metadata);
    guarded.put("embeddingProvider", embeddingService.providerName());
    if (!removedLines.isEmpty()) {
      guarded.put("promptInjectionLinesRemoved", removedLines.size());
    }
    if (truncated) {
      guarded.put("truncatedAtCharacters", properties.maxContentCharacters());
    }
    return guarded;
  }

  private EvidenceQueryResponse citedAnswer(
      AiSystem system, String question, List<EvidenceRetriever.Passage> passages) {
    List<Citation> citations = passages.stream()
        .map(passage -> new Citation(
            passage.document().id().toString(),
            passage.document().title(),
            passage.chunk().sectionRef(),
            passage.quote()))
        .toList();
    double confidence = Math.max(0.35, Math.min(0.95, passages.stream()
        .mapToDouble(EvidenceRetriever.Passage::similarity)
        .max()
        .orElse(0.35)));
    EvidenceRetriever.Passage strongest = passages.get(0);
    String answer = """
        Based on indexed evidence for %s, the strongest cited material (%s, %s) says: %s

        For the question "%s", review the cited sections before changing the %s release decision.
        """
        .formatted(system.name(), strongest.document().title(), strongest.chunk().sectionRef(), strongest.quote(),
            question, system.releaseDecision())
        .strip();
    return new EvidenceQueryResponse(answer, confidence, citations);
  }

  /** No chunk scored high enough: say so, and cite nothing rather than documents that may not exist. */
  private EvidenceQueryResponse fallbackAnswer(AiSystem system) {
    String gaps = system.openGaps().isEmpty() ? "none recorded" : String.join("; ", system.openGaps());
    return new EvidenceQueryResponse(
        "No indexed evidence for %s matched this question, so there is nothing to cite. Upload the document that answers it, then ask again. %s currently has a %s release decision. Open gaps: %s."
            .formatted(system.name(), system.name(), system.releaseDecision(), gaps),
        0.0,
        List.of());
  }

  private String checksum(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not calculate evidence checksum", e);
    }
  }

}
