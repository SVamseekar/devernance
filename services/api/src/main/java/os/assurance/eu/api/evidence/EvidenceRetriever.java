package os.assurance.eu.api.evidence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Hybrid retrieval: a keyword (BM25) ranking and a meaning (embedding) ranking of the system's chunks, merged by
 * reciprocal rank fusion. Keywords find exact terms such as "Article 14" or "false refusal rate" that a small
 * embedding model blurs; embeddings find passages that say the same thing in other words. Fusion needs no
 * calibration between BM25 scores and cosine similarities, only the two rank orders.
 *
 * <p>Only the few cited chunks are read in full; each citation quotes the sentences that match the question best.
 */
@Component
public class EvidenceRetriever {
  /** The constant from the reciprocal rank fusion paper (Cormack et al., 2009), used by most hybrid search. */
  static final int RRF_K = 60;
  /** Candidates taken from each ranking before fusion. */
  static final int CANDIDATES = 50;
  static final int SNIPPET_CHARACTERS = 320;
  /** A supporting citation's similarity to the question, relative to the best citation's. */
  static final double SUPPORTING_SIMILARITY = 0.75;

  private final EvidenceSearchIndex index;
  private final EvidenceRepository repository;
  private final EvidenceEmbeddingService embeddings;
  private final EvidenceProperties properties;

  public EvidenceRetriever(
      EvidenceSearchIndex index,
      EvidenceRepository repository,
      EvidenceEmbeddingService embeddings,
      EvidenceProperties properties) {
    this.index = index;
    this.repository = repository;
    this.embeddings = embeddings;
    this.properties = properties;
  }

  public List<Passage> retrieve(List<EvidenceDocument> documents, String question) {
    if (documents.isEmpty()) {
      return List.of();
    }
    List<UUID> documentIds = documents.stream().map(EvidenceDocument::id).toList();
    Map<UUID, EvidenceDocument> documentsById = documents.stream()
        .collect(Collectors.toMap(EvidenceDocument::id, Function.identity()));
    String questionEmbedding = embeddings.embed(question);
    List<EvidenceSearchIndex.RankedChunk> keywords = index.byKeywords(documentIds, question, CANDIDATES);
    List<EvidenceSearchIndex.RankedChunk> meaning =
        index.byMeaning(documentIds, embeddings.providerName(), questionEmbedding, CANDIDATES);

    int questionTerms = EvidenceTerms.distinct(question).size();
    int requiredTerms = Math.min(2, Math.max(1, questionTerms));
    Map<UUID, Double> fused = new HashMap<>();
    Map<UUID, Boolean> relevant = new HashMap<>();
    for (int rank = 0; rank < keywords.size(); rank++) {
      EvidenceSearchIndex.RankedChunk hit = keywords.get(rank);
      fused.merge(hit.chunkId(), 1.0 / (RRF_K + rank + 1), Double::sum);
      if (hit.matchedTerms() >= requiredTerms) {
        relevant.put(hit.chunkId(), true);
      }
    }
    for (int rank = 0; rank < meaning.size(); rank++) {
      EvidenceSearchIndex.RankedChunk hit = meaning.get(rank);
      fused.merge(hit.chunkId(), 1.0 / (RRF_K + rank + 1), Double::sum);
      if (hit.score() >= properties.minRetrievalScore()) {
        relevant.put(hit.chunkId(), true);
      }
    }
    // A chunk is cited only when its keywords or its meaning match on their own; fusion then orders them.
    List<UUID> ordered = fused.entrySet().stream()
        .filter(entry -> relevant.containsKey(entry.getKey()))
        .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
        .map(Map.Entry::getKey)
        .limit(CANDIDATES)
        .toList();
    Map<UUID, EvidenceChunk> chunks = repository.findChunksByIds(ordered).stream()
        .collect(Collectors.toMap(EvidenceChunk::id, Function.identity()));

    Set<String> terms = EvidenceTerms.distinct(question);
    List<Passage> passages = new ArrayList<>();
    for (UUID id : ordered) {
      EvidenceChunk chunk = chunks.get(id);
      EvidenceDocument document = chunk == null ? null : documentsById.get(chunk.documentId());
      if (document == null || overlapsCited(passages, chunk)) {
        continue;
      }
      double similarity = embeddings.providerName().equals(chunk.embeddingProvider())
          ? embeddings.similarity(questionEmbedding, chunk.embedding())
          : 0.0;
      // The best passage is cited on its keywords or its meaning; a further passage must also be close in
      // meaning to the best one, which keeps reference lists and indexes that merely repeat the terms out.
      if (!passages.isEmpty() && similarity < Math.max(
          properties.minRetrievalScore(), SUPPORTING_SIMILARITY * passages.get(0).similarity())) {
        continue;
      }
      passages.add(new Passage(chunk, document, similarity, quote(chunk.content(), terms)));
      if (passages.size() >= properties.maxRetrievedChunks()) {
        break;
      }
    }
    return passages;
  }

  public String searchDescription() {
    return index.description();
  }

  /** Overlapping neighbours and a second hit on the same page repeat a citation; keep the better-ranked one. */
  private static boolean overlapsCited(List<Passage> cited, EvidenceChunk chunk) {
    return cited.stream().anyMatch(passage -> passage.chunk().documentId().equals(chunk.documentId())
        && (Math.abs(passage.chunk().ordinal() - chunk.ordinal()) <= 1
            || (passage.chunk().sectionRef() != null && passage.chunk().sectionRef().startsWith("p. ")
                && passage.chunk().sectionRef().equals(chunk.sectionRef()))));
  }

  /** The sentence with the most question terms, followed by the next sentences while they fit. */
  static String quote(String content, Set<String> terms) {
    List<String> sentences = EvidenceChunker.sentences(content).stream()
        .map(words -> String.join(" ", words))
        .toList();
    if (sentences.isEmpty()) {
      return "";
    }
    int best = 0;
    long bestScore = -1;
    for (int i = 0; i < sentences.size(); i++) {
      long score = EvidenceTerms.distinct(sentences.get(i)).stream().filter(terms::contains).count();
      if (score > bestScore) {
        best = i;
        bestScore = score;
      }
    }
    StringBuilder quote = new StringBuilder(sentences.get(best));
    for (int i = best + 1; i < sentences.size()
        && quote.length() + 1 + sentences.get(i).length() <= SNIPPET_CHARACTERS; i++) {
      quote.append(' ').append(sentences.get(i));
    }
    String text = quote.toString();
    return text.length() <= SNIPPET_CHARACTERS ? text : text.substring(0, SNIPPET_CHARACTERS - 3).strip() + "...";
  }

  /** A cited passage: the chunk, its document, its cosine similarity to the question, and the quoted text. */
  public record Passage(EvidenceChunk chunk, EvidenceDocument document, double similarity, String quote) {
  }
}
