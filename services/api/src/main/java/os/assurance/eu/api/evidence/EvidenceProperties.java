package os.assurance.eu.api.evidence;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "assurance.evidence")
public class EvidenceProperties {
  private int maxContentCharacters = 2_000_000;
  private int maxMetadataEntries = 25;
  private int maxMetadataValueCharacters = 1_000;
  private int maxQuestionCharacters = 2_048;
  private int maxRetrievedChunks = 4;
  private double minRetrievalScore = 0.20;
  private String embeddingProvider = "local-hash";
  private List<String> allowedSourceSchemes = List.of("memory", "s3", "gs", "https");

  public int maxContentCharacters() {
    return maxContentCharacters;
  }

  public void setMaxContentCharacters(int maxContentCharacters) {
    this.maxContentCharacters = maxContentCharacters;
  }

  public int maxMetadataEntries() {
    return maxMetadataEntries;
  }

  public void setMaxMetadataEntries(int maxMetadataEntries) {
    this.maxMetadataEntries = maxMetadataEntries;
  }

  public int maxMetadataValueCharacters() {
    return maxMetadataValueCharacters;
  }

  public void setMaxMetadataValueCharacters(int maxMetadataValueCharacters) {
    this.maxMetadataValueCharacters = maxMetadataValueCharacters;
  }

  public int maxQuestionCharacters() {
    return maxQuestionCharacters;
  }

  public void setMaxQuestionCharacters(int maxQuestionCharacters) {
    this.maxQuestionCharacters = maxQuestionCharacters;
  }

  public int maxRetrievedChunks() {
    return maxRetrievedChunks;
  }

  public void setMaxRetrievedChunks(int maxRetrievedChunks) {
    this.maxRetrievedChunks = maxRetrievedChunks;
  }

  public double minRetrievalScore() {
    return minRetrievalScore;
  }

  public void setMinRetrievalScore(double minRetrievalScore) {
    this.minRetrievalScore = minRetrievalScore;
  }

  public String embeddingProvider() {
    return embeddingProvider;
  }

  public void setEmbeddingProvider(String embeddingProvider) {
    this.embeddingProvider = embeddingProvider;
  }

  public List<String> allowedSourceSchemes() {
    return allowedSourceSchemes;
  }

  public void setAllowedSourceSchemes(List<String> allowedSourceSchemes) {
    this.allowedSourceSchemes = allowedSourceSchemes;
  }
}
