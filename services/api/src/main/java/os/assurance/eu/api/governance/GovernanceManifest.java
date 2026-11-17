package os.assurance.eu.api.governance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One manifest as sent by `devernance apply`: the envelope ({@code apiVersion}, {@code kind}, {@code metadata})
 * plus its checked spec. Exactly one of {@code system} and {@code policy} is set, matching {@code kind}.
 */
public record GovernanceManifest(
    GovernanceResourceKind kind,
    String name,
    /** For an AISystem only: bind to this already-registered system instead of registering a new one. */
    UUID registrySystemId,
    Map<String, Object> spec,
    SystemSpec system,
    PolicySpec policy) {

  public static final String API_VERSION = "devernance.io/v1";
  static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,127}$");
  static final int MAX_SPEC_BYTES = 64 * 1024;
  static final int MAX_MANIFESTS = 50;

  /** What is hashed to tell whether a manifest changed: its kind, name and spec, in canonical JSON. */
  public String contentSha256() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("kind", kind.manifestKind());
    content.put("name", name);
    content.put("spec", spec);
    return GovernanceJson.sha256Hex(GovernanceJson.canonical(content));
  }

  /**
   * Checks every manifest and returns the usable ones. Each problem is added to {@code errors} with the
   * manifest it belongs to, e.g. {@code GovernancePolicy/claims-policy: spec.rules[0].decision: is required}.
   */
  @SuppressWarnings("unchecked")
  public static List<GovernanceManifest> parseAll(List<Map<String, Object>> raw, List<String> errors) {
    List<GovernanceManifest> manifests = new ArrayList<>();
    if (raw == null || raw.isEmpty()) {
      errors.add("resources: send at least one manifest");
      return manifests;
    }
    if (raw.size() > MAX_MANIFESTS) {
      errors.add("resources: send at most " + MAX_MANIFESTS + " manifests at once");
      return manifests;
    }
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < raw.size(); i++) {
      Map<String, Object> manifest = raw.get(i);
      List<String> own = new ArrayList<>();
      String label = "resources[" + i + "]";
      if (manifest == null) {
        errors.add(label + ": must be an object");
        continue;
      }
      SpecReader read = new SpecReader(own);
      read.onlyKeys("manifest", manifest, Set.of("apiVersion", "kind", "metadata", "spec"));
      if (!API_VERSION.equals(manifest.get("apiVersion"))) {
        own.add("apiVersion: must be " + API_VERSION);
      }
      GovernanceResourceKind kind = manifest.get("kind") instanceof String text
          ? GovernanceResourceKind.fromManifestKind(text).orElse(null) : null;
      if (kind == null) {
        own.add("kind: must be AISystem or GovernancePolicy");
      }
      Map<String, Object> metadata = read.object("manifest", manifest, "metadata");
      read.onlyKeys("metadata", metadata, Set.of("name", "systemId"));
      String name = read.string("metadata", metadata, "name", true, 128);
      if (name != null && !NAME.matcher(name).matches()) {
        own.add("metadata.name: use lowercase letters, digits and . _ - (start with a letter or digit)");
        name = null;
      }
      UUID registrySystemId = null;
      if (metadata.get("systemId") != null) {
        if (kind != GovernanceResourceKind.AI_SYSTEM) {
          own.add("metadata.systemId: only an AISystem can name a registered system");
        } else {
          try {
            registrySystemId = UUID.fromString(String.valueOf(metadata.get("systemId")));
          } catch (IllegalArgumentException invalid) {
            own.add("metadata.systemId: must be the id of a registered system");
          }
        }
      }
      Map<String, Object> spec = read.object("manifest", manifest, "spec");
      if (manifest.get("spec") == null) {
        own.add("spec: is required");
      }
      if (GovernanceJson.utf8Length(GovernanceJson.canonical(spec)) > MAX_SPEC_BYTES) {
        own.add("spec: must be at most " + MAX_SPEC_BYTES / 1024 + " KB");
        spec = Map.of();
      }
      SystemSpec system = null;
      PolicySpec policy = null;
      if (kind == GovernanceResourceKind.AI_SYSTEM) {
        system = SystemSpec.parse(spec, own);
      } else if (kind == GovernanceResourceKind.GOVERNANCE_POLICY) {
        policy = PolicySpec.parse(spec, own);
      }
      if (kind != null && name != null && !seen.add(kind + "/" + name)) {
        own.add("appears more than once in this request");
      }
      if (kind != null && name != null) {
        label = kind.manifestKind() + "/" + name;
      }
      if (own.isEmpty()) {
        manifests.add(new GovernanceManifest(kind, name, registrySystemId, spec, system, policy));
      } else {
        for (String problem : own) {
          errors.add(label + ": " + problem);
        }
      }
    }
    return manifests;
  }
}
