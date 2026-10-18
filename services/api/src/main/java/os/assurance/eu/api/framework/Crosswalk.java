package os.assurance.eu.api.framework;

import java.util.List;

/**
 * A framework crosswalk or checklist loaded from {@code classpath:crosswalks/*.json}. It claims relevance only:
 * each item lists the controls and evidence types that bear on it. Live only once {@code reviewedBy} is set.
 * {@code type} is {@code crosswalk} (a framework's items) or {@code checklist} (a law's duties, each with a source).
 */
public record Crosswalk(
    String framework,
    String type,
    String version,
    String source,
    String retrieved,
    String reviewedBy,
    List<Item> items) {

  public static final String CROSSWALK = "crosswalk";
  public static final String CHECKLIST = "checklist";

  public Crosswalk {
    type = type == null || type.isBlank() ? CROSSWALK : type;
    reviewedBy = reviewedBy == null ? "" : reviewedBy;
    items = items == null ? List.of() : List.copyOf(items);
  }

  public Crosswalk(String framework, String version, String source, String retrieved, String reviewedBy,
      List<Item> items) {
    this(framework, CROSSWALK, version, source, retrieved, reviewedBy, items);
  }

  public boolean reviewed() {
    return !reviewedBy.isBlank();
  }

  public record Item(
      String id, String title, List<String> controlCodes, List<String> evidenceTypes, String sourceUrl) {
    public Item {
      controlCodes = controlCodes == null ? List.of() : List.copyOf(controlCodes);
      evidenceTypes = evidenceTypes == null ? List.of() : List.copyOf(evidenceTypes);
    }

    public Item(String id, String title, List<String> controlCodes, List<String> evidenceTypes) {
      this(id, title, controlCodes, evidenceTypes, null);
    }
  }
}
