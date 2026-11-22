package os.assurance.eu.api.evidence;

import java.util.List;
import java.util.UUID;

/**
 * Ranks the chunks of a set of documents two ways: by keywords (BM25) and by meaning (embedding similarity).
 * The caller passes only the documents of one system of the current tenant.
 */
interface EvidenceSearchIndex {
  List<RankedChunk> byKeywords(List<UUID> documentIds, String question, int limit);

  List<RankedChunk> byMeaning(List<UUID> documentIds, String provider, String questionEmbedding, int limit);

  /** Where the ranking runs, for the audit trail and the logs. */
  String description();

  /**
   * One ranked chunk. {@code score} is BM25 for a keyword ranking and cosine similarity for a meaning ranking;
   * {@code matchedTerms} counts the distinct question terms the chunk contains (0 for a meaning ranking).
   */
  record RankedChunk(UUID chunkId, double score, int matchedTerms) {
  }
}
