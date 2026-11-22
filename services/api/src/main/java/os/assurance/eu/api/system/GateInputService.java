package os.assurance.eu.api.system;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import os.assurance.eu.api.evidence.EvidenceRepository;

/** Derives gate inputs from indexed evidence. See spec: required evidence types per risk class. */
@Service
public class GateInputService {
  public static final String GAP_PREFIX = "Missing evidence: ";
  public static final String NO_EVAL_GAP = "No completed eval run";
  public static final Map<RiskClass, List<String>> REQUIRED_EVIDENCE = Map.of(
      RiskClass.HIGH, List.of("DPIA", "MODEL_CARD", "POLICY", "CONTROL_MAP", "VENDOR_DOC"),
      RiskClass.LIMITED, List.of("MODEL_CARD", "POLICY"),
      RiskClass.MINIMAL, List.of("POLICY"),
      RiskClass.PROHIBITED, List.of());

  private final EvidenceRepository evidence;
  private final boolean manualInputs;

  public GateInputService(
      EvidenceRepository evidence,
      @Value("${assurance.gate.manual-inputs:true}") boolean manualInputs) {
    this.evidence = evidence;
    this.manualInputs = manualInputs;
  }

  public boolean manualInputs() {
    return manualInputs;
  }

  public static int coverage(RiskClass riskClass, Collection<String> indexedTypes) {
    List<String> required = REQUIRED_EVIDENCE.getOrDefault(riskClass, List.of());
    if (required.isEmpty()) {
      return 0;
    }
    Set<String> present = normalize(indexedTypes);
    long have = required.stream().filter(present::contains).count();
    return (int) Math.round(100.0 * have / required.size());
  }

  public static List<String> missingEvidenceGaps(RiskClass riskClass, Collection<String> indexedTypes) {
    Set<String> present = normalize(indexedTypes);
    List<String> gaps = new ArrayList<>();
    for (String type : REQUIRED_EVIDENCE.getOrDefault(riskClass, List.of())) {
      if (!present.contains(type)) {
        gaps.add(GAP_PREFIX + type);
      }
    }
    return gaps;
  }

  /** Copy of the system with coverage and evidence gaps recomputed; manual gaps are kept. */
  public AiSystem recompute(AiSystem system) {
    // A document with lines removed by the prompt-injection guard is still that document; count it.
    List<String> types = evidence.findDocumentsBySystemId(system.id()).stream()
        .filter(d -> "indexed".equalsIgnoreCase(d.ingestionStatus())
            || "indexed_with_warnings".equalsIgnoreCase(d.ingestionStatus()))
        .map(d -> d.type())
        .toList();
    List<String> gaps = new ArrayList<>(system.openGaps().stream()
        .filter(g -> !g.startsWith(GAP_PREFIX) && !NO_EVAL_GAP.equals(g))
        .toList());
    gaps.addAll(missingEvidenceGaps(system.riskClass(), types));
    if (system.evalScore() == 0) {
      gaps.add(NO_EVAL_GAP);
    }
    return new AiSystem(
        system.id(), system.name(), system.owner(), system.purpose(), system.riskClass(), system.riskBasis(),
        system.deploymentRegion(), coverage(system.riskClass(), types), system.evalScore(),
        system.dataContractStatus(), system.releaseDecision(), gaps, system.vendorName(), system.modelName(),
        system.modelVersion(), system.dataSources(), system.sector(), system.decisionImpact(),
        system.affectedUsers(), system.createdAt(), Instant.now());
  }

  private static Set<String> normalize(Collection<String> types) {
    return types == null ? Set.of() : types.stream()
        .filter(t -> t != null && !t.isBlank())
        .map(t -> t.trim().toUpperCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }
}
