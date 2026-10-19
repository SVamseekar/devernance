package os.assurance.eu.api.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

class DjlSentenceEmbeddingProviderTest {
  private static final String UNREACHABLE = "http://127.0.0.1:9/never";

  @Test
  void reusesAFileThatMatchesItsPinnedChecksumWithoutDownloading(@TempDir Path dir) throws Exception {
    Path file = Files.writeString(dir.resolve("tokenizer.json"), "{}", StandardCharsets.UTF_8);
    var pinned = new DjlSentenceEmbeddingProvider.ModelFile(
        "tokenizer.json", UNREACHABLE, DjlSentenceEmbeddingProvider.sha256(file));

    assertThat(DjlSentenceEmbeddingProvider.ensure(HttpClient.newHttpClient(), dir, pinned)).isEqualTo(file);
  }

  @Test
  void doesNotTrustAFileWhoseChecksumDiffers(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("model.onnx"), "tampered", StandardCharsets.UTF_8);
    var pinned = new DjlSentenceEmbeddingProvider.ModelFile("model.onnx", UNREACHABLE, "0".repeat(64));

    // It tries to fetch a fresh copy instead of loading the mismatched one; here the fetch fails.
    assertThatThrownBy(() -> DjlSentenceEmbeddingProvider.ensure(HttpClient.newHttpClient(), dir, pinned))
        .isInstanceOf(Exception.class);
    assertThat(Files.exists(dir.resolve("model.onnx.part"))).isFalse();
  }

  /** Loads the real model (about 90 MB, cached in DJL_MODEL_DIR). Run with RUN_DJL_TESTS=1. */
  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_DJL_TESTS", matches = "1")
  void embedsLongTextAndRanksRelatedTextHigher() throws Exception {
    Path dir = Path.of(System.getenv().getOrDefault("DJL_MODEL_DIR", "target/djl-model"));
    DjlSentenceEmbeddingProvider provider = new DjlSentenceEmbeddingProvider(dir);
    try {
      String longText = "oversight ".repeat(5_000);
      String vector = provider.embed(longText);
      String[] parts = vector.split(",");
      assertThat(parts).hasSize(384);
      double norm = 0;
      for (String part : parts) {
        norm += Math.pow(Double.parseDouble(part), 2);
      }
      assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));

      String question = provider.embed("Can a human reviewer override the model's routing decision?");
      String related = provider.embed("Reviewers may override automated claim routing at any time.");
      String unrelated = provider.embed("The office kitchen is cleaned every Friday afternoon.");
      assertThat(provider.similarity(question, related)).isGreaterThan(provider.similarity(question, unrelated));
    } finally {
      provider.close();
    }
  }
}
