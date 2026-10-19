package os.assurance.eu.api.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlmMapClient {
  static final String ADAPTER_VERSION = "meta-llama/Llama-3.2-3B-Instruct";

  private final ObjectMapper objectMapper;
  private final HttpClient http;
  private volatile String endpoint;
  private volatile boolean unreachable;

  public SlmMapClient(
      ObjectMapper objectMapper,
      @Value("${assurance.slm.map-endpoint:}") String endpoint) {
    this.objectMapper = objectMapper;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    this.endpoint = endpoint == null ? "" : endpoint.trim();
  }

  public boolean configured() {
    return endpoint != null && !endpoint.isBlank();
  }

  public boolean down() {
    return !configured() || unreachable;
  }

  void setEndpointForTest(String value) {
    endpoint = value == null ? "" : value.trim();
    unreachable = false;
  }

  public Outcome map(String title, String text, List<String> candidateIds) {
    if (!configured()) {
      return Outcome.down();
    }
    try {
      String body = objectMapper.writeValueAsString(new Request(title, text, candidateIds));
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
          .timeout(Duration.ofSeconds(2))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        unreachable = true;
        return Outcome.down();
      }
      unreachable = false;
      return parse(response.body(), candidateIds);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      unreachable = true;
      return Outcome.down();
    } catch (Exception ex) {
      unreachable = true;
      return Outcome.down();
    }
  }

  private Outcome parse(String body, List<String> candidateIds) {
    final JsonNode node;
    try {
      node = objectMapper.readTree(body);
    } catch (Exception ex) {
      return Outcome.invalid();
    }
    if (node == null || !node.isObject()) {
      return Outcome.invalid();
    }
    String relation = text(node, "relation");
    if (!List.of("supports", "overlaps", "tension_candidate", "abstain").contains(relation)) {
      return Outcome.invalid();
    }
    if ("abstain".equals(relation)) {
      return Outcome.abstain();
    }
    String obligationId = text(node, "obligation_id");
    if (obligationId == null) {
      obligationId = text(node, "provision_key");
    }
    if (obligationId == null || candidateIds == null || !candidateIds.contains(obligationId)) {
      return Outcome.invalid();
    }
    return Outcome.candidate(relation, obligationId);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isTextual()) {
      return null;
    }
    String text = value.asText().trim();
    return text.isEmpty() ? null : text;
  }

  public record Outcome(Kind kind, String relation, String obligationId) {
    public static Outcome down() {
      return new Outcome(Kind.DOWN, null, null);
    }

    public static Outcome invalid() {
      return new Outcome(Kind.INVALID, null, null);
    }

    public static Outcome abstain() {
      return new Outcome(Kind.ABSTAIN, "abstain", null);
    }

    public static Outcome candidate(String relation, String obligationId) {
      return new Outcome(Kind.CANDIDATE, relation, obligationId);
    }
  }

  public enum Kind {
    DOWN,
    INVALID,
    ABSTAIN,
    CANDIDATE
  }

  private record Request(String title, String text, List<String> candidateIds) {
  }
}
