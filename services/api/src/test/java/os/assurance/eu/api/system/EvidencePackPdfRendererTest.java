package os.assurance.eu.api.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidencePackPdfRendererTest {

  @Test
  void renderProducesPdfMagicBytes() {
    Instant generatedAt = Instant.parse("2026-07-20T12:00:00Z");
    UUID systemId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    EvidencePackResponse pack = new EvidencePackResponse(
        systemId,
        generatedAt,
        ReleaseDecision.REVIEW,
        Map.of("riskClass", "LIMITED", "basis", "test"),
        List.of(Map.of("coverage", 50, "openGaps", List.of())),
        List.of(Map.of("latestScore", 80, "releaseDecision", "REVIEW")),
        List.of(),
        List.of(),
        List.of(),
        Map.of(
            "disclaimer",
            "Assisted determination (not legal advice). Suggested applicability / obligation map only.",
            "status",
            "NONE"),
        Map.of("disclaimer", "Assisted conformity dossier."),
        Map.of("howto", "evgraph scan-promotion --gate"),
        "1.1",
        "a".repeat(64),
        "devernance-api/0.1.0",
        "chain-head-example",
        "",
        List.of(),
        List.of(),
        List.of(),
        Map.of(),
        Map.of(),
        MonitoringPlan.standIn(),
        "test-signature",
        null);

    byte[] pdf = EvidencePackPdfRenderer.render(pack, "Claims Triage AI");

    assertThat(pdf).isNotEmpty();
    String header = new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.ISO_8859_1);
    assertThat(header).startsWith("%PDF");
    assertThat(EvidencePackPdfRenderer.filename(systemId, generatedAt))
        .isEqualTo("evidence-pack-33333333-3333-3333-3333-333333333333-2026-07-20.pdf");
  }

  @Test
  void theFrameworksSectionListsEachItemWithTheDisclaimer() throws Exception {
    Map<String, Object> nist = Map.of(
        "framework", "nist-ai-rmf",
        "version", "1.0",
        "reviewed", false,
        "disclaimer", "Mapping shows which evidence is relevant to each framework item. It is not an assessment of whether the system meets the framework.",
        "items", List.of(Map.of("id", "GOVERN 2", "title", "Accountability structures", "status", "covered")));
    EvidencePackResponse pack = new EvidencePackResponse(
        UUID.randomUUID(), Instant.parse("2026-10-05T12:00:00Z"), ReleaseDecision.REVIEW, Map.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), "1.1", "a".repeat(64), "gen",
        null, "", List.of(), List.of(), List.of(), Map.of(), Map.of(), MonitoringPlan.standIn(), "sig",
        Map.of("nist-ai-rmf", nist));

    byte[] pdf = EvidencePackPdfRenderer.render(pack, "Claims Triage AI");

    com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
    com.lowagie.text.pdf.parser.PdfTextExtractor extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
    StringBuilder text = new StringBuilder();
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      text.append(extractor.getTextFromPage(page)).append('\n');
    }
    String flat = text.toString().replaceAll("\\s+", " ");
    assertThat(flat).contains("Framework crosswalks");
    assertThat(flat).contains("GOVERN 2");
    assertThat(flat).contains("covered");
    assertThat(flat).contains("It is not an assessment of whether the system meets the framework.");
  }
}
