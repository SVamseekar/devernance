package os.assurance.eu.api.system;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditEvent;

/**
 * Renders a sealed evidence pack as a simple multi-section PDF (Phase 6 polish).
 * JSON remains the primary machine-readable pack format.
 */
public final class EvidencePackPdfRenderer {
  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATETIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private EvidencePackPdfRenderer() {
  }

  public static byte[] render(EvidencePackResponse pack, String systemName) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 48, 48, 48, 48);
      PdfWriter.getInstance(document, out);
      document.open();

      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
      Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
      Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
      Font monoFont = FontFactory.getFont(FontFactory.COURIER, 8);

      document.add(new Paragraph("Devernance — Evidence Pack", titleFont));
      document.add(new Paragraph(" ", bodyFont));
      document.add(new Paragraph(
          "JSON is the primary sealed export. This PDF is a human-readable Phase 6 export.",
          bodyFont));
      document.add(new Paragraph(
          "Assisted obligation determination sections are not legal advice and require human legal review.",
          bodyFont));
      document.add(new Paragraph(" ", bodyFont));

      addHeading(document, headingFont, "1. System identity");
      addLine(document, bodyFont, "System ID", pack.systemId());
      addLine(document, bodyFont, "Name", systemName == null ? "" : systemName);
      addLine(document, bodyFont, "Generated at", formatInstant(pack.generatedAt()));
      addLine(document, bodyFont, "Release decision", pack.decision());
      addLine(document, bodyFont, "Pack version", pack.evidencePackVersion());
      addLine(document, bodyFont, "Generator", pack.generator());

      addHeading(document, headingFont, "2. Risk classification");
      addMapLines(document, bodyFont, pack.riskClassification());

      addHeading(document, headingFont, "3. Evidence summary");
      for (Map<String, Object> row : pack.evidence()) {
        addMapLines(document, bodyFont, row);
      }

      addHeading(document, headingFont, "4. Eval runs");
      for (Map<String, Object> row : pack.evalRuns()) {
        addMapLines(document, bodyFont, row);
      }

      addHeading(document, headingFont, "5. Data contracts");
      if (pack.dataContracts().isEmpty()) {
        addLine(document, bodyFont, "Contracts", "(none)");
      } else {
        for (Map<String, Object> row : pack.dataContracts()) {
          addLine(document, bodyFont, "Contract",
              String.valueOf(row.getOrDefault("name", row.get("id"))));
          addLine(document, bodyFont, "  Status", row.get("status"));
          addLine(document, bodyFont, "  Version", row.get("version"));
        }
      }

      addHeading(document, headingFont, "6. Approvals");
      if (pack.approvals().isEmpty()) {
        addLine(document, bodyFont, "Workflows", "(none)");
      } else {
        for (Map<String, Object> row : pack.approvals()) {
          addLine(document, bodyFont, "Workflow", row.get("workflowId"));
          addLine(document, bodyFont, "  Trigger", row.get("trigger"));
          addLine(document, bodyFont, "  Status", row.get("status"));
        }
      }

      addHeading(document, headingFont, "7. Assisted obligation determination");
      Map<String, Object> determination = pack.determination();
      if (determination == null || determination.isEmpty()) {
        addLine(document, bodyFont, "Determination", "(none)");
      } else {
        addLine(document, bodyFont, "Disclaimer", determination.get("disclaimer"));
        addLine(document, bodyFont, "Product label", determination.get("productLabel"));
        addLine(document, bodyFont, "Run ID", determination.get("runId"));
        addLine(document, bodyFont, "Ruleset", determination.get("rulesetVersion"));
        addLine(document, bodyFont, "Status", determination.get("status"));
        Object obligations = determination.get("obligations");
        if (obligations instanceof List<?> list && !list.isEmpty()) {
          for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
              addLine(document, bodyFont, "Obligation",
                  row.get("ruleCode") + " — " + row.get("applicability"));
            }
          }
        }
      }

      addHeading(document, headingFont, "8. Checklist");
      Map<String, Object> conformity = pack.conformity();
      if (conformity == null || conformity.isEmpty()) {
        addLine(document, bodyFont, "Checklist", "(none)");
      } else {
        addLine(document, bodyFont, "Disclaimer", conformity.get("disclaimer"));
        addLine(document, bodyFont, "Updated at", conformity.get("updatedAt"));
      }

      addHeading(document, headingFont, "9. Evgraph artifacts (library JSON trio)");
      Map<String, Object> evgraph = pack.evgraphArtifacts();
      if (evgraph == null || evgraph.isEmpty()) {
        addLine(document, bodyFont, "Evgraph", "(none)");
      } else {
        addLine(document, bodyFont, "How to run", evgraph.get("howto"));
        if (evgraph.get("disclaimer") != null) {
          addLine(document, bodyFont, "Public claims", evgraph.get("disclaimer"));
        }
        if (evgraph.get("howtoDataset") != null) {
          addLine(document, bodyFont, "Dataset scan", evgraph.get("howtoDataset"));
        }
      }

      addHeading(document, headingFont, "10. Audit excerpt");
      List<AuditEvent> audits = pack.auditEvents();
      int limit = Math.min(audits.size(), 15);
      if (limit == 0) {
        addLine(document, bodyFont, "Events", "(none)");
      } else {
        for (int i = 0; i < limit; i++) {
          AuditEvent event = audits.get(i);
          addLine(document, bodyFont, "Event",
              event.eventType() + " @ " + formatInstant(event.createdAt()));
        }
        if (audits.size() > limit) {
          addLine(document, bodyFont, "…", (audits.size() - limit) + " more events in JSON pack");
        }
      }

      addHeading(document, headingFont, "11. Post-market monitoring stand-in");
      Map<String, Object> monitoringPlan = pack.monitoringPlan();
      if (monitoringPlan == null || monitoringPlan.isEmpty()) {
        addLine(document, bodyFont, "Plan", "(none)");
      } else {
        addLine(document, bodyFont, "Commission template due", monitoringPlan.get("commissionTemplateDue"));
        addLine(document, bodyFont, "Signals", "eval drift, contract drift, reg-monitor diff");
        addLine(document, bodyFont, "Annex III technical-documentation monitoring", "FUTURE until 2027-12-02");
        addLine(document, bodyFont, "Annex I", "FUTURE until 2028-08-02");
      }

      Map<String, Object> frameworks = pack.frameworks();
      if (frameworks != null && !frameworks.isEmpty()) {
        addHeading(document, headingFont, "12. Framework crosswalks");
        for (Object value : frameworks.values()) {
          if (!(value instanceof Map<?, ?> view)) {
            continue;
          }
          addLine(document, bodyFont, "Framework", view.get("framework") + " " + view.get("version")
              + (Boolean.TRUE.equals(view.get("reviewed")) ? "" : " (mapping not yet reviewed)"));
          addLine(document, bodyFont, "Note", view.get("disclaimer"));
          if (view.get("items") instanceof List<?> items) {
            for (Object itemObj : items) {
              if (itemObj instanceof Map<?, ?> item) {
                addLine(document, bodyFont, String.valueOf(item.get("id")), item.get("status"));
              } else if (itemObj != null) {
                Map<?, ?> item = MAPPER.convertValue(itemObj, Map.class);
                addLine(document, bodyFont, String.valueOf(item.get("id")), item.get("status"));
              }
            }
          }
        }
      }

      addHeading(document, headingFont, frameworks != null && !frameworks.isEmpty() ? "13. Seal" : "12. Seal");
      addLine(document, bodyFont, "contentSha256", pack.contentSha256());
      String signature = pack.signature() == null ? "" : pack.signature();
      String preview = signature.length() <= 64 ? signature : signature.substring(0, 64) + "…";
      addLine(document, bodyFont, "Signature",
          "Signature (RS256, verify with /.well-known/jwks.json): " + preview);
      if (pack.auditChainHead() != null && !pack.auditChainHead().isBlank()) {
        addLine(document, bodyFont, "auditChainHead", pack.auditChainHead());
      }
      document.add(new Paragraph(" ", bodyFont));
      document.add(new Paragraph(
          "contentSha256=" + pack.contentSha256(),
          monoFont));

      document.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IllegalStateException("Unable to render evidence pack PDF", e);
    }
  }

  public static String filename(UUID systemId, Instant generatedAt) {
    String date = generatedAt == null ? "unknown" : DATE.format(generatedAt);
    return "evidence-pack-" + systemId + "-" + date + ".pdf";
  }

  private static void addHeading(Document document, Font font, String text) throws DocumentException {
    document.add(new Paragraph(" ", font));
    document.add(new Paragraph(text, font));
  }

  private static void addLine(Document document, Font font, String label, Object value)
      throws DocumentException {
    document.add(new Paragraph(label + ": " + (value == null ? "" : value), font));
  }

  private static void addMapLines(Document document, Font font, Map<String, Object> map)
      throws DocumentException {
    if (map == null || map.isEmpty()) {
      addLine(document, font, "Data", "(empty)");
      return;
    }
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      addLine(document, font, entry.getKey(), entry.getValue());
    }
  }

  private static String formatInstant(Instant instant) {
    return instant == null ? "" : DATETIME.format(instant);
  }
}
