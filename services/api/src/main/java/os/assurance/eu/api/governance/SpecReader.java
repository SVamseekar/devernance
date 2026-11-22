package os.assurance.eu.api.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads fields out of a manifest map and collects every problem instead of stopping at the first, so one
 * `devernance validate` run lists all of them. Unknown keys are errors: a misspelled key must never silently
 * turn into a missing rule.
 */
final class SpecReader {
  private final List<String> errors;

  SpecReader(List<String> errors) {
    this.errors = errors;
  }

  void error(String message) {
    errors.add(message);
  }

  /** Reports keys of {@code map} that are not in {@code allowed}. */
  void onlyKeys(String path, Map<String, Object> map, Set<String> allowed) {
    for (String key : map.keySet()) {
      if (!allowed.contains(key)) {
        error(path + ": unknown key '" + key + "' (allowed: " + String.join(", ", allowed.stream().sorted().toList())
            + ")");
      }
    }
  }

  /** The object at {@code key}, or an empty map when it is absent. Reports a value that is not an object. */
  @SuppressWarnings("unchecked")
  Map<String, Object> object(String path, Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map<?, ?> nested && nested.keySet().stream().allMatch(String.class::isInstance)) {
      return (Map<String, Object>) nested;
    }
    error(path + "." + key + ": must be an object");
    return Map.of();
  }

  /** The list at {@code key}, or an empty list when it is absent. Reports a value that is not a list. */
  List<Object> list(String path, Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> items) {
      return new ArrayList<>(items);
    }
    error(path + "." + key + ": must be a list");
    return List.of();
  }

  /** The non-blank string at {@code key}, or null (with an error when {@code required}). */
  String string(String path, Map<String, Object> map, String key, boolean required, int maxLength) {
    Object value = map.get(key);
    if (value == null) {
      if (required) {
        error(path + "." + key + ": is required");
      }
      return null;
    }
    if (!(value instanceof String text) || text.isBlank()) {
      error(path + "." + key + ": must be a non-empty string");
      return null;
    }
    if (text.length() > maxLength) {
      error(path + "." + key + ": must be at most " + maxLength + " characters");
      return null;
    }
    return text.trim();
  }

  /** A list of non-blank strings at {@code key}; reports and skips anything else. */
  List<String> strings(String path, Map<String, Object> map, String key, int maxItems, int maxLength) {
    List<Object> raw = list(path, map, key);
    if (raw.size() > maxItems) {
      error(path + "." + key + ": must have at most " + maxItems + " entries");
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      Object item = raw.get(i);
      if (item instanceof String text && !text.isBlank() && text.length() <= maxLength) {
        out.add(text.trim());
      } else {
        error(path + "." + key + "[" + i + "]: must be a non-empty string of at most " + maxLength + " characters");
      }
    }
    return out;
  }

  /** One of {@code allowed} (compared without case), or {@code fallback} when the key is absent. */
  String choice(String path, Map<String, Object> map, String key, Set<String> allowed, String fallback) {
    Object value = map.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof String text && allowed.contains(text.trim().toLowerCase(java.util.Locale.ROOT))) {
      return text.trim().toLowerCase(java.util.Locale.ROOT);
    }
    error(path + "." + key + ": must be one of " + String.join(", ", allowed.stream().sorted().toList()));
    return fallback;
  }
}
