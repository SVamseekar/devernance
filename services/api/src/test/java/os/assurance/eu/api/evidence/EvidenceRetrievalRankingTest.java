package os.assurance.eu.api.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EvidenceRetrievalRankingTest {

  private static EvidenceChunkJpaRepository.ChunkText text(UUID id, String content) {
    return new EvidenceChunkJpaRepository.ChunkText() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public String getContent() {
        return content;
      }
    };
  }

  @Test
  void bm25RanksTheChunkWithTheRareTermFirst() {
    UUID common = UUID.randomUUID();
    UUID rare = UUID.randomUUID();
    UUID unrelated = UUID.randomUUID();
    EvidenceRepository repository = Mockito.mock(EvidenceRepository.class);
    Mockito.when(repository.findChunkTexts(Mockito.anyList())).thenReturn(List.of(
        text(common, "The model card describes the model, the model owner and the model version."),
        text(rare, "Applicants may request a human-only review of an underwriting decision by the model."),
        text(unrelated, "The office kitchen is cleaned every Friday."),
        text(UUID.randomUUID(), "The model is retrained each quarter."),
        text(UUID.randomUUID(), "Model monitoring alerts go to the on-call engineer.")));
    var index = new InMemoryEvidenceSearchIndex(repository, null);

    var ranked = index.byKeywords(List.of(UUID.randomUUID()), "How can an applicant get a human-only review of the model?", 10);

    assertThat(ranked.get(0).chunkId()).isEqualTo(rare);
    assertThat(ranked.get(0).matchedTerms()).isGreaterThanOrEqualTo(4);
    assertThat(ranked).extracting(EvidenceSearchIndex.RankedChunk::chunkId).doesNotContain(unrelated);
  }

  @Test
  void stemmingMatchesPluralsAndVerbForms() {
    assertThat(EvidenceTerms.of("Approvals were tested")).containsExactly("approval", "test");
    assertThat(EvidenceTerms.of("approval testing")).containsExactly("approval", "test");
    assertThat(EvidenceTerms.of("policies and the process")).containsExactly("policy", "process");
  }

  @Test
  void quotesTheSentenceThatAnswersTheQuestion() {
    String chunk = "Section 4 covers retention. Logs are kept for six years in the EU region. "
        + "Applicants may request a human-only review within 30 days. Reviews are logged.";

    String quote = EvidenceRetriever.quote(chunk, EvidenceTerms.distinct("How do applicants request a human-only review?"));

    assertThat(quote).startsWith("Applicants may request a human-only review within 30 days.");
    assertThat(quote.length()).isLessThanOrEqualTo(EvidenceRetriever.SNIPPET_CHARACTERS);
  }
}
