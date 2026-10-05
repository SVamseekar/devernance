package os.assurance.eu.api.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic content seal for evidence packs.
 *
 * <p>Canonical form: the payload converted to plain JSON values (records become objects), every object's keys
 * sorted alphabetically, ISO-8601 instants, then SHA-256 over UTF-8 bytes. A verifier who parses the exported
 * JSON pack gets the same canonical form. The {@code contentSha256} field is never included
 * in the hashed payload.
 */
public final class EvidencePackSealer {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private EvidencePackSealer() {
  }

  /**
   * Compute content SHA-256 for a pack payload map that must not contain contentSha256.
   */
  public static String contentSha256(Map<String, Object> sealPayload) {
    try {
      Object plain = MAPPER.convertValue(sealPayload == null ? Map.of() : sealPayload, Object.class);
      String canonical = MAPPER.writeValueAsString(plain);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to seal evidence pack", e);
    }
  }

  /**
   * Build the ordered seal payload from pack fields (excludes contentSha256).
   */
  public static Map<String, Object> sealPayload(
      String evidencePackVersion,
      String generator,
      String auditChainHead,
      Object systemId,
      Object generatedAt,
      Object decision,
      Object riskClassification,
      Object evidence,
      Object evalRuns,
      Object dataContracts,
      Object approvals,
      Object auditEvents) {
    return sealPayload(
        evidencePackVersion,
        generator,
        auditChainHead,
        systemId,
        generatedAt,
        decision,
        riskClassification,
        evidence,
        evalRuns,
        dataContracts,
        approvals,
        auditEvents,
        Map.of(),
        Map.of(),
        Map.of(),
        "",
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        Map.of(),
        Map.of(),
        MonitoringPlan.standIn());
  }

  public static Map<String, Object> sealPayload(
      String evidencePackVersion,
      String generator,
      String auditChainHead,
      Object systemId,
      Object generatedAt,
      Object decision,
      Object riskClassification,
      Object evidence,
      Object evalRuns,
      Object dataContracts,
      Object approvals,
      Object auditEvents,
      Object determination) {
    return sealPayload(
        evidencePackVersion,
        generator,
        auditChainHead,
        systemId,
        generatedAt,
        decision,
        riskClassification,
        evidence,
        evalRuns,
        dataContracts,
        approvals,
        auditEvents,
        determination,
        Map.of(),
        Map.of(),
        "",
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        Map.of(),
        Map.of(),
        MonitoringPlan.standIn());
  }

  public static Map<String, Object> sealPayload(
      String evidencePackVersion,
      String generator,
      String auditChainHead,
      Object systemId,
      Object generatedAt,
      Object decision,
      Object riskClassification,
      Object evidence,
      Object evalRuns,
      Object dataContracts,
      Object approvals,
      Object auditEvents,
      Object determination,
      Object conformity,
      Object evgraphArtifacts,
      Object corpusVersion,
      Object acceptedLinks,
      Object queue,
      Object currentGaps,
      Object acceptedArtifacts,
      Object evidenceCounts,
      Object monitoringPlan) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("acceptedArtifacts", acceptedArtifacts == null ? Map.of() : acceptedArtifacts);
    payload.put("acceptedLinks", acceptedLinks == null ? java.util.List.of() : acceptedLinks);
    payload.put("corpusVersion", corpusVersion == null ? "" : corpusVersion);
    payload.put("currentGaps", currentGaps == null ? java.util.List.of() : currentGaps);
    payload.put("queue", queue == null ? java.util.List.of() : queue);
    payload.put("approvals", approvals);
    payload.put("auditChainHead", auditChainHead);
    payload.put("auditEvents", auditEvents);
    payload.put("conformity", conformity == null ? Map.of() : conformity);
    payload.put("dataContracts", dataContracts);
    payload.put("decision", decision);
    payload.put("determination", determination == null ? Map.of() : determination);
    payload.put("evalRuns", evalRuns);
    payload.put("evgraphArtifacts", evgraphArtifacts == null ? Map.of() : evgraphArtifacts);
    payload.put("evidence", evidence);
    payload.put("evidenceCounts", evidenceCounts == null ? Map.of() : evidenceCounts);
    payload.put("evidencePackVersion", evidencePackVersion);
    payload.put("generatedAt", generatedAt);
    payload.put("generator", generator);
    payload.put("monitoringPlan", monitoringPlan == null ? MonitoringPlan.standIn() : monitoringPlan);
    payload.put("riskClassification", riskClassification);
    payload.put("systemId", systemId);
    return payload;
  }
}
