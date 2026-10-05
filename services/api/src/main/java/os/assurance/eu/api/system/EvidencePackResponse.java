package os.assurance.eu.api.system;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditEvent;

/**
 * Sealed evidence pack. JSON is the primary machine-readable format (PRD MVP).
 * Seal fields (contentSha256, evidencePackVersion, generator, auditChainHead)
 * make exports deterministic and traceable under a fixed clock.
 */
public record EvidencePackResponse(
    UUID systemId,
    Instant generatedAt,
    ReleaseDecision decision,
    Map<String, Object> riskClassification,
    List<Map<String, Object>> evidence,
    List<Map<String, Object>> evalRuns,
    List<Map<String, Object>> dataContracts,
    List<Map<String, Object>> approvals,
    List<AuditEvent> auditEvents,
    Map<String, Object> determination,
    @JsonIgnore Map<String, Object> conformity,
    Map<String, Object> evgraphArtifacts,
    String evidencePackVersion,
    String contentSha256,
    String generator,
    String auditChainHead,
    String corpusVersion,
    List<Map<String, Object>> acceptedLinks,
    List<Map<String, Object>> queue,
    List<Map<String, Object>> currentGaps,
    Map<String, Object> acceptedArtifacts,
    Map<String, Integer> evidenceCounts,
    Map<String, Object> monitoringPlan,
    String signature,
    /** Framework crosswalk views keyed by framework; absent (null) when the plan lacks the feature. */
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> frameworks) {
}
