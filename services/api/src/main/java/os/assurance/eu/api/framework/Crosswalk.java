package os.assurance.eu.api.framework;

import java.util.List;

/**
 * A framework crosswalk loaded from {@code classpath:crosswalks/*.json}. It claims relevance, not conformity:
 * each item lists the controls and evidence types that bear on it. Live only once {@code reviewedBy} is set.
 */
public record Crosswalk(
    String framework,
    String version,
    String source,
    String retrieved,
    String reviewedBy,
    List<Item> items) {

  public Crosswalk {
    reviewedBy = reviewedBy == null ? "" : reviewedBy;
    items = items == null ? List.of() : List.copyOf(items);
  }

  public boolean reviewed() {
    return !reviewedBy.isBlank();
  }

  public record Item(String id, String title, List<String> controlCodes, List<String> evidenceTypes) {
    public Item {
      controlCodes = controlCodes == null ? List.of() : List.copyOf(controlCodes);
      evidenceTypes = evidenceTypes == null ? List.of() : List.copyOf(evidenceTypes);
    }
  }
}
