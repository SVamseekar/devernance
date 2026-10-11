package os.assurance.eu.api.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.assurance.eu.api.billing.Feature;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.control.ControlStatus;
import os.assurance.eu.api.control.SystemControl;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.sector.SectorControlDef;
import os.assurance.eu.api.sector.SectorPack;
import os.assurance.eu.api.sector.SectorPackRegistry;

/** Loads and validates framework crosswalks at startup and computes per-system coverage. */
@Service
public class CrosswalkService {
  public static final String DISCLAIMER =
      "Mapping shows which evidence is relevant to each framework item. It is not an assessment of whether the system meets the framework.";
  /** Framework key to the plan feature that unlocks it. Only these keys are served. */
  public static final Map<String, Feature> FEATURES = Map.of(
      "nist-ai-rmf", Feature.NIST_CROSSWALK,
      "iso-42001", Feature.ISO_CROSSWALK,
      "colorado-sb26-189", Feature.US_STATE_PACKS);
  public static final Set<String> EVIDENCE_TYPES = Set.of("DPIA", "POLICY", "MODEL_CARD", "VENDOR_DOC", "CONTROL_MAP");

  /** status is covered, partial, or no_evidence; controls/evidence are the mapped ones that are satisfied. */
  public record ItemStatus(
      String id,
      String title,
      String status,
      List<String> controls,
      List<String> evidence,
      List<String> requiredControls,
      List<String> requiredEvidence,
      String sourceUrl) {}

  private final Map<String, Crosswalk> byFramework;
  private final ControlService controls;
  private final EvidenceRepository evidence;
  private final boolean serveUnreviewed;

  public CrosswalkService(
      ObjectMapper mapper, SectorPackRegistry sectorPacks, ControlService controls, EvidenceRepository evidence,
      @Value("${assurance.frameworks.serve-unreviewed:false}") boolean serveUnreviewed) {
    this.controls = controls;
    this.evidence = evidence;
    this.serveUnreviewed = serveUnreviewed;
    Set<String> known = knownControlCodes(sectorPacks);
    Map<String, Crosswalk> loaded = new LinkedHashMap<>();
    for (Crosswalk cw : load(mapper)) {
      validate(cw, known);
      if (loaded.put(cw.framework(), cw) != null) {
        throw new IllegalStateException("Duplicate crosswalk for framework " + cw.framework());
      }
    }
    this.byFramework = Map.copyOf(loaded);
  }

  /** Baseline control codes plus the extra controls of every enabled sector pack. */
  public static Set<String> knownControlCodes(SectorPackRegistry sectorPacks) {
    Set<String> known = new HashSet<>(ControlService.baselineCodes());
    for (SectorPack pack : sectorPacks.enabledPacks()) {
      pack.extraControls().stream().map(SectorControlDef::code).forEach(known::add);
    }
    return known;
  }

  /** Reads every {@code classpath:crosswalks/*.json}. Fails fast on unreadable files. */
  public static List<Crosswalk> load(ObjectMapper mapper) {
    try {
      Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:crosswalks/*.json");
      List<Crosswalk> out = new ArrayList<>();
      for (Resource resource : resources) {
        try (InputStream in = resource.getInputStream()) {
          out.add(mapper.readValue(in, Crosswalk.class));
        } catch (IOException e) {
          throw new IllegalStateException("Unreadable crosswalk " + resource.getFilename(), e);
        }
      }
      return out;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to list crosswalks", e);
    }
  }

  /** Throws {@link IllegalStateException} naming the first problem. Run at startup, never at request time. */
  public static void validate(Crosswalk cw, Set<String> knownControlCodes) {
    if (cw.framework() == null || cw.framework().isBlank()) {
      throw new IllegalStateException("Crosswalk without a framework key");
    }
    if (!Crosswalk.CROSSWALK.equals(cw.type()) && !Crosswalk.CHECKLIST.equals(cw.type())) {
      throw new IllegalStateException("Crosswalk " + cw.framework() + " has unknown type " + cw.type());
    }
    if (cw.source() == null || cw.source().isBlank()) {
      throw new IllegalStateException("Crosswalk " + cw.framework() + " has no source");
    }
    if (cw.items().isEmpty()) {
      throw new IllegalStateException("Crosswalk " + cw.framework() + " has no items");
    }
    Set<String> ids = new HashSet<>();
    for (Crosswalk.Item item : cw.items()) {
      if (item.id() == null || item.id().isBlank() || item.title() == null || item.title().isBlank()) {
        throw new IllegalStateException("Crosswalk " + cw.framework() + " has an item without id or title");
      }
      if (!ids.add(item.id())) {
        throw new IllegalStateException("Crosswalk " + cw.framework() + " repeats item " + item.id());
      }
      if (Crosswalk.CHECKLIST.equals(cw.type()) && (item.sourceUrl() == null || item.sourceUrl().isBlank())) {
        throw new IllegalStateException("Checklist " + cw.framework() + " item " + item.id() + " has no sourceUrl");
      }
      for (String code : item.controlCodes()) {
        if (!knownControlCodes.contains(code)) {
          throw new IllegalStateException(
              "Crosswalk " + cw.framework() + " item " + item.id() + " references unknown control code " + code);
        }
      }
      for (String type : item.evidenceTypes()) {
        if (!EVIDENCE_TYPES.contains(type)) {
          throw new IllegalStateException(
              "Crosswalk " + cw.framework() + " item " + item.id() + " references unknown evidence type " + type);
        }
      }
    }
  }

  /** True for a stored, searchable document, including one whose suspicious lines were removed on ingestion. */
  public static boolean isIndexed(String ingestionStatus) {
    return "indexed".equalsIgnoreCase(ingestionStatus) || "indexed_with_warnings".equalsIgnoreCase(ingestionStatus);
  }

  /** A crosswalk is live once it has been reviewed against its source, or when unreviewed ones are allowed. */
  public static boolean live(Crosswalk crosswalk, boolean serveUnreviewed) {
    return serveUnreviewed || crosswalk.reviewed();
  }

  /** The crosswalk for a framework key, only while it is live; the API, the pack and exports all go through here. */
  public Optional<Crosswalk> find(String framework) {
    return Optional.ofNullable(byFramework.get(framework)).filter(cw -> live(cw, serveUnreviewed));
  }

  public Crosswalk get(String framework) {
    return find(framework).orElseThrow(() -> new IllegalArgumentException("Unknown framework " + framework));
  }

  @Transactional(readOnly = true)
  public List<ItemStatus> coverage(String framework, UUID systemId) {
    Map<String, ControlStatus> statuses = controls.listForSystem(systemId).stream()
        .collect(Collectors.toMap(SystemControl::controlCode, SystemControl::status,
            (a, b) -> a == ControlStatus.PASS || b == ControlStatus.PASS ? ControlStatus.PASS : a));
    Set<String> indexed = evidence.findDocumentsBySystemId(systemId).stream()
        .filter(d -> isIndexed(d.ingestionStatus()))
        .map(d -> d.type())
        .collect(Collectors.toSet());
    return coverage(get(framework), statuses, indexed);
  }

  /** The framework view used by the API and the evidence pack (same map in both). */
  @Transactional(readOnly = true)
  public Map<String, Object> view(String framework, UUID systemId) {
    Crosswalk cw = get(framework);
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("framework", cw.framework());
    view.put("type", cw.type());
    view.put("version", cw.version());
    view.put("source", cw.source());
    view.put("reviewed", cw.reviewed());
    view.put("disclaimer", DISCLAIMER);
    view.put("items", coverage(framework, systemId));
    return view;
  }

  public static List<ItemStatus> coverage(
      Crosswalk cw, Map<String, ControlStatus> controlStatus, Set<String> indexedTypes) {
    List<ItemStatus> out = new ArrayList<>();
    for (Crosswalk.Item item : cw.items()) {
      List<String> passing = item.controlCodes().stream()
          .filter(code -> controlStatus.get(code) == ControlStatus.PASS)
          .toList();
      List<String> present = item.evidenceTypes().stream().filter(indexedTypes::contains).toList();
      String status;
      if (!passing.isEmpty() && !present.isEmpty()) {
        status = "covered";
      } else if (!passing.isEmpty() || !present.isEmpty()) {
        status = "partial";
      } else {
        status = "no_evidence";
      }
      out.add(new ItemStatus(item.id(), item.title(), status, passing, present,
          item.controlCodes(), item.evidenceTypes(), item.sourceUrl()));
    }
    return out;
  }
}
