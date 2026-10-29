package os.assurance.eu.api.evidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ranks in the API process by reading the chunks of the system. Used on H2 (local runs and tests) and, for the
 * meaning ranking only, on a Postgres without pgvector. Postgres with pgvector ranks both ways in SQL.
 */
class InMemoryEvidenceSearchIndex implements EvidenceSearchIndex {
  static final double K1 = 1.2;
  static final double B = 0.75;

  private final EvidenceRepository repository;
  private final EvidenceEmbeddingService embeddings;

  InMemoryEvidenceSearchIndex(EvidenceRepository repository, EvidenceEmbeddingService embeddings) {
    this.repository = repository;
    this.embeddings = embeddings;
  }

  @Override
  public List<RankedChunk> byKeywords(List<UUID> documentIds, String question, int limit) {
    Set<String> queryTerms = EvidenceTerms.distinct(question);
    List<EvidenceChunkJpaRepository.ChunkText> chunks = repository.findChunkTexts(documentIds);
    if (queryTerms.isEmpty() || chunks.isEmpty()) {
      return List.of();
    }
    List<Map<String, Integer>> frequencies = new ArrayList<>(chunks.size());
    List<Integer> lengths = new ArrayList<>(chunks.size());
    Map<String, Integer> documentFrequency = new HashMap<>();
    long totalLength = 0;
    for (EvidenceChunkJpaRepository.ChunkText chunk : chunks) {
      List<String> terms = EvidenceTerms.of(chunk.getContent());
      Map<String, Integer> tf = new HashMap<>();
      for (String term : terms) {
        if (queryTerms.contains(term)) {
          tf.merge(term, 1, Integer::sum);
        }
      }
      tf.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
      frequencies.add(tf);
      lengths.add(terms.size());
      totalLength += terms.size();
    }
    double averageLength = Math.max(1.0, (double) totalLength / chunks.size());
    List<RankedChunk> ranked = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      Map<String, Integer> tf = frequencies.get(i);
      if (tf.isEmpty()) {
        continue;
      }
      double score = 0;
      for (Map.Entry<String, Integer> entry : tf.entrySet()) {
        score += bm25(entry.getValue(), documentFrequency.get(entry.getKey()), chunks.size(),
            lengths.get(i), averageLength);
      }
      ranked.add(new RankedChunk(chunks.get(i).getId(), score, tf.size()));
    }
    ranked.sort(Comparator.comparingDouble(RankedChunk::score).reversed());
    return ranked.subList(0, Math.min(limit, ranked.size()));
  }

  /** Okapi BM25 term weight with the Lucene IDF, which is never negative. */
  static double bm25(double tf, double df, double chunkCount, double length, double averageLength) {
    double idf = Math.log(1 + (chunkCount - df + 0.5) / (df + 0.5));
    return idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * length / averageLength));
  }

  @Override
  public List<RankedChunk> byMeaning(List<UUID> documentIds, String provider, String questionEmbedding, int limit) {
    // Chunks embedded by another provider (before a provider switch) live in another vector space.
    return repository.findChunkVectors(documentIds).stream()
        .filter(vector -> provider.equals(vector.getEmbeddingProvider()))
        .map(vector -> new RankedChunk(
            vector.getId(), embeddings.similarity(questionEmbedding, vector.getEmbedding()), 0))
        .sorted(Comparator.comparingDouble(RankedChunk::score).reversed())
        .limit(limit)
        .toList();
  }

  @Override
  public String description() {
    return "in-memory";
  }
}
