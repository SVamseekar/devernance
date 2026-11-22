package os.assurance.eu.api.governance;

import java.util.Optional;

/** The kinds of manifest `devernance apply` accepts. {@code manifestKind} is the value of {@code kind:} in YAML. */
public enum GovernanceResourceKind {
  AI_SYSTEM("AISystem"),
  GOVERNANCE_POLICY("GovernancePolicy");

  private final String manifestKind;

  GovernanceResourceKind(String manifestKind) {
    this.manifestKind = manifestKind;
  }

  public String manifestKind() {
    return manifestKind;
  }

  public static Optional<GovernanceResourceKind> fromManifestKind(String kind) {
    for (GovernanceResourceKind value : values()) {
      if (value.manifestKind.equals(kind)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
