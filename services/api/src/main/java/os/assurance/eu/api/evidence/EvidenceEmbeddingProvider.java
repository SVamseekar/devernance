package os.assurance.eu.api.evidence;

public interface EvidenceEmbeddingProvider {
  String name();

  String embed(String text);

  /** Embeds many texts; providers that can run a batch through the model at once override this. */
  default java.util.List<String> embedAll(java.util.List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  double similarity(String left, String right);
}
