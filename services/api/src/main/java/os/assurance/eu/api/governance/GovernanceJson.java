package os.assurance.eu.api.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * One way to turn a value into text and a hash, used wherever governance records are hashed or exported:
 * JSON with every object's keys sorted, no extra whitespace, ISO-8601 instants, UTF-8, SHA-256.
 */
public final class GovernanceJson {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private GovernanceJson() {
  }

  /** Canonical JSON text of {@code value}. Records and maps are both written as objects with sorted keys. */
  public static String canonical(Object value) {
    try {
      // Through a plain tree first, so record fields are sorted the same way as map keys.
      Object plain = MAPPER.convertValue(value, Object.class);
      return MAPPER.writeValueAsString(plain);
    } catch (Exception e) {
      throw new IllegalStateException("Could not serialize governance record", e);
    }
  }

  public static String sha256Hex(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public static int utf8Length(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }
}
