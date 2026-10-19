package os.assurance.eu.api.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
      PlatformTransactionManager transactionManager) {
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
  }

  /**
   * Fetching, parsing and embedding run before the transaction opens: a slow source URL or a long document must
   * not hold a database connection. Only the inserts, the audit entry and the gate recalculation are transactional.
   */
  public EvidenceDocumentResponse ingest(CreateEvidenceDocumentRequest request) {
    evidenceGuard.validateDocumentRequest(request);
    String extracted = textExtractionService.extract(request);
    SanitizedText sanitized = promptInjectionGuard.sanitizeDocumentText(extracted);
    List<ChunkDraft> chunkDrafts = chunker.chunk(sanitized.text());
    List<String> embeddings = chunkDrafts.stream().map(draft -> embeddingService.embed(draft.content())).toList();
    Map<String, Object> metadata = evidenceGuard.sanitizeMetadata(request.metadata());
    return transaction.execute(status -> persist(request, sanitized, chunkDrafts, embeddings, metadata));
  }

  private EvidenceDocumentResponse persist(
      CreateEvidenceDocumentRequest request,
      SanitizedText sanitized,
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
        sanitized.removedLines().isEmpty() ? "indexed" : "indexed_with_warnings",
        now));
    repository.saveChunks(toChunks(saved, chunkDrafts, embeddings, metadata, sanitized.removedLines()));
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
            "removedPromptInjectionLines", sanitized.removedLines().size()));
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
    String questionEmbedding = embeddingService.embed(sanitizedQuestion);
    List<EvidenceDocument> documents = repository.findDocumentsBySystemId(system.id());
    Map<UUID, EvidenceDocument> documentsById = documents.stream()
        .collect(Collectors.toMap(EvidenceDocument::id, Function.identity()));
    // Score on embeddings alone, then load the text of the few chunks that are cited. Chunks embedded by a
    // different provider (before a provider switch) live in another vector space and cannot be compared.
    String provider = embeddingService.providerName();
    Map<UUID, Double> topScores = new LinkedHashMap<>();
    repository.findChunkVectors(documents.stream().map(EvidenceDocument::id).toList()).stream()
        .filter(vector -> provider.equals(vector.getEmbeddingProvider()))
        .map(vector -> Map.entry(vector.getId(), embeddingService.similarity(questionEmbedding, vector.getEmbedding())))
        .filter(scored -> scored.getValue() >= properties.minRetrievalScore())
        .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
        .limit(properties.maxRetrievedChunks())
        .forEach(scored -> topScores.put(scored.getKey(), scored.getValue()));
    Map<UUID, EvidenceChunk> chunksById = repository.findChunksByIds(List.copyOf(topScores.keySet())).stream()
        .collect(Collectors.toMap(EvidenceChunk::id, Function.identity()));
    List<ScoredChunk> scoredChunks = topScores.entrySet().stream()
        .filter(scored -> chunksById.containsKey(scored.getKey()))
        .map(scored -> {
          EvidenceChunk chunk = chunksById.get(scored.getKey());
          return new ScoredChunk(chunk, documentsById.get(chunk.documentId()), scored.getValue());
        })
        .filter(scoredChunk -> scoredChunk.document() != null)
        .toList();

    EvidenceQueryResponse response = scoredChunks.isEmpty()
        ? fallbackAnswer(system)
        : citedAnswer(system, sanitizedQuestion, scoredChunks);
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
            "citationCount", response.citations().size()));
    return response;
  }

  private List<EvidenceChunk> toChunks(
      EvidenceDocument document,
      List<ChunkDraft> chunkDrafts,
      List<String> embeddings,
      Map<String, Object> requestMetadata,
      List<String> removedLines) {
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
              metadataWithGuard(metadata, removedLines));
        })
        .toList();
  }

  private Map<String, Object> metadataWithGuard(Map<String, Object> metadata, List<String> removedLines) {
    java.util.LinkedHashMap<String, Object> guarded = new java.util.LinkedHashMap<>(metadata);
    guarded.put("embeddingProvider", embeddingService.providerName());
    if (removedLines.isEmpty()) {
      return guarded;
    }
    guarded.put("promptInjectionLinesRemoved", removedLines.size());
    return guarded;
  }

  private EvidenceQueryResponse citedAnswer(AiSystem system, String question, List<ScoredChunk> scoredChunks) {
    List<Citation> citations = scoredChunks.stream()
        .map(scored -> new Citation(
            scored.document().id().toString(),
            scored.document().title(),
            scored.chunk().sectionRef(),
            snippet(scored.chunk().content())))
        .toList();
    double confidence = Math.max(0.35, Math.min(0.95, scoredChunks.stream()
        .mapToDouble(ScoredChunk::score)
        .average()
        .orElse(0.35)));
    String strongest = citations.get(0).snippet();
    String answer = """
        Based on indexed evidence for %s, the strongest cited material says: %s

        For the question "%s", review the cited sections before changing the %s release decision.
        """
        .formatted(system.name(), strongest, question, system.releaseDecision())
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

  private String snippet(String content) {
    String normalized = content.replaceAll("\\s+", " ").strip();
    return normalized.length() <= 220 ? normalized : normalized.substring(0, 217) + "...";
  }

  private String checksum(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not calculate evidence checksum", e);
    }
  }

  private record ScoredChunk(EvidenceChunk chunk, EvidenceDocument document, double score) {
  }
}
