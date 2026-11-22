package os.assurance.eu.api.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EvidenceChunkerTest {
  private final EvidenceChunker chunker = new EvidenceChunker();

  private static String sentences(int count, int wordsEach) {
    return IntStream.range(0, count)
        .mapToObj(i -> "s" + i + " " + "word ".repeat(wordsEach - 2) + "end.")
        .collect(Collectors.joining(" "));
  }

  @Test
  void keepsSentencesWholeAndWithinTheModelsInputSize() {
    List<EvidenceChunker.ChunkDraft> chunks = chunker.chunk(sentences(40, 20));

    assertThat(chunks).hasSizeGreaterThan(5);
    for (EvidenceChunker.ChunkDraft chunk : chunks) {
      assertThat(chunk.content().split(" ").length).isLessThanOrEqualTo(EvidenceChunker.MAX_WORDS);
      assertThat(chunk.content()).startsWith("s").endsWith("end.");
      assertThat(chunk.sectionRef()).startsWith("Section ");
    }
  }

  @Test
  void consecutiveChunksShareTheirBoundarySentence() {
    List<EvidenceChunker.ChunkDraft> chunks = chunker.chunk(sentences(40, 20));

    String lastOfFirst = chunks.get(0).content().substring(chunks.get(0).content().lastIndexOf(" s") + 1);
    assertThat(chunks.get(1).content()).startsWith(lastOfFirst);
  }

  @Test
  void chunksOfAPagedDocumentStayOnTheirPage() {
    String text = "Intro page text." + TextExtractionService.PAGE_BREAK + "\n\n"
        + TextExtractionService.PAGE_BREAK + sentences(12, 20);

    List<EvidenceChunker.ChunkDraft> chunks = chunker.chunk(text);

    assertThat(chunks.get(0)).isEqualTo(new EvidenceChunker.ChunkDraft("p. 1", "Intro page text."));
    assertThat(chunks.subList(1, chunks.size()))
        .extracting(EvidenceChunker.ChunkDraft::sectionRef)
        .containsOnly("p. 3");
  }

  @Test
  void cutsARunWithoutSentenceEndsSuchAsATable() {
    List<EvidenceChunker.ChunkDraft> chunks = chunker.chunk("cell ".repeat(1_000));

    assertThat(chunks).allSatisfy(chunk ->
        assertThat(chunk.content().split(" ").length).isLessThanOrEqualTo(EvidenceChunker.MAX_WORDS));
    assertThat(chunks.stream().mapToInt(chunk -> chunk.content().split(" ").length).sum()).isGreaterThanOrEqualTo(1_000);
  }

  @Test
  void neverExceedsTheContentColumn() {
    List<EvidenceChunker.ChunkDraft> chunks = chunker.chunk(("x".repeat(400) + " ").repeat(60));

    assertThat(chunks).allSatisfy(chunk ->
        assertThat(chunk.content().length()).isLessThanOrEqualTo(EvidenceChunker.MAX_CHARACTERS));
  }

  @Test
  void saysSoWhenThereIsNoText() {
    assertThat(chunker.chunk("  \n ")).extracting(EvidenceChunker.ChunkDraft::content)
        .containsExactly("No extractable text was found.");
  }
}
