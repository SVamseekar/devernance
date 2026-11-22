package os.assurance.eu.api.evidence;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvidenceEmbeddingService {
  private final EvidenceEmbeddingProvider provider;

  public EvidenceEmbeddingService(List<EvidenceEmbeddingProvider> providers, EvidenceProperties properties) {
    this.provider = providers.stream()
        .filter(candidate -> candidate.name().equalsIgnoreCase(properties.embeddingProvider()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "No evidence embedding provider configured for " + properties.embeddingProvider()));
  }

  public String providerName() {
    return provider.name();
  }

  public String embed(String text) {
    return provider.embed(text);
  }

  public List<String> embedAll(List<String> texts) {
    return provider.embedAll(texts);
  }

  public double similarity(String left, String right) {
    return provider.similarity(left, right);
  }
}
