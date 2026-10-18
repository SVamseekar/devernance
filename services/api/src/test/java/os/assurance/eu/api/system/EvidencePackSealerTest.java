package os.assurance.eu.api.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidencePackSealerTest {

  @Test
  void contentSha256IsDeterministicForIdenticalPayload() {
    Instant fixed = Instant.parse("2026-07-20T12:00:00Z");
    UUID systemId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    Map<String, Object> first = EvidencePackSealer.sealPayload(
        "1.0",
        "devernance-api/0.1.0",
        "abc123chainhead",
        systemId,
        fixed,
        ReleaseDecision.REVIEW,
        Map.of("riskClass", "LIMITED", "basis", "demo"),
        List.of(Map.of("coverage", 40, "openGaps", List.of("gap-a"))),
        List.of(Map.of("latestScore", 70, "releaseDecision", "REVIEW")),
        List.of(),
        List.of(),
        List.of());

    Map<String, Object> second = EvidencePackSealer.sealPayload(
        "1.0",
        "devernance-api/0.1.0",
        "abc123chainhead",
        systemId,
        fixed,
        ReleaseDecision.REVIEW,
        Map.of("riskClass", "LIMITED", "basis", "demo"),
        List.of(Map.of("coverage", 40, "openGaps", List.of("gap-a"))),
        List.of(Map.of("latestScore", 70, "releaseDecision", "REVIEW")),
        List.of(),
        List.of(),
        List.of());

    String hash1 = EvidencePackSealer.contentSha256(first);
    String hash2 = EvidencePackSealer.contentSha256(second);

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).hasSize(64);
    assertThat(hash1).matches("[0-9a-f]{64}");
  }

  @Test
  void contentSha256ChangesWhenBusinessFieldChanges() {
    Instant fixed = Instant.parse("2026-07-20T12:00:00Z");
    UUID systemId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    Map<String, Object> base = EvidencePackSealer.sealPayload(
        "1.0",
        "devernance-api/0.1.0",
        null,
        systemId,
        fixed,
        ReleaseDecision.PASS,
        Map.of("riskClass", "MINIMAL"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());

    Map<String, Object> changed = EvidencePackSealer.sealPayload(
        "1.0",
        "devernance-api/0.1.0",
        null,
        systemId,
        fixed,
        ReleaseDecision.BLOCKED,
        Map.of("riskClass", "MINIMAL"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());

    assertThat(EvidencePackSealer.contentSha256(base))
        .isNotEqualTo(EvidencePackSealer.contentSha256(changed));
  }

  @Test
  void fixedClockInstantIsPartOfSeal() {
    UUID systemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    Map<String, Object> t1 = EvidencePackSealer.sealPayload(
        "1.0", "gen", null, systemId, Instant.parse("2026-01-01T00:00:00Z"),
        ReleaseDecision.PASS, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    Map<String, Object> t2 = EvidencePackSealer.sealPayload(
        "1.0", "gen", null, systemId, Instant.parse("2026-01-01T00:00:01Z"),
        ReleaseDecision.PASS, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    assertThat(EvidencePackSealer.contentSha256(t1))
        .isNotEqualTo(EvidencePackSealer.contentSha256(t2));
  }

  record Event(String zeta, String alpha) {}

  @Test
  void recordFieldOrderDoesNotChangeTheHashSoExportedJsonCanBeRehashed() {
    // A verifier parses the exported JSON into maps; the server hashes records. Both must canonicalise the same.
    Map<String, Object> asRecord = Map.of("auditEvents", List.of(new Event("z", "a")));
    Map<String, Object> asMap = Map.of("auditEvents", List.of(Map.of("alpha", "a", "zeta", "z")));
    assertThat(EvidencePackSealer.contentSha256(asRecord)).isEqualTo(EvidencePackSealer.contentSha256(asMap));
  }
}
