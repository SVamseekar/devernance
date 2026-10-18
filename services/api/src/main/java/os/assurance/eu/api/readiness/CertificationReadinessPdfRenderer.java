package os.assurance.eu.api.readiness;

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
import java.util.UUID;

/**
 * Human-readable certification readiness report PDF.
 * JSON remains the primary machine-readable export.
 */
public final class CertificationReadinessPdfRenderer {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATETIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private CertificationReadinessPdfRenderer() {
  }

  public static byte[] render(CertificationReadinessResponse report) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 48, 48, 48, 48);
      PdfWriter.getInstance(document, out);
      document.open();

      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
      Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
      Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

      document.add(new Paragraph("Devernance — Certification Readiness Report", titleFont));
      document.add(new Paragraph(" ", bodyFont));
      document.add(new Paragraph(report.disclaimer(), bodyFont));
      document.add(new Paragraph(" ", bodyFont));

      document.add(new Paragraph("1. Summary", headingFont));
      addLine(document, bodyFont, "System", report.systemName());
      addLine(document, bodyFont, "System ID", report.systemId());
      addLine(document, bodyFont, "Generated at", formatInstant(report.generatedAt()));
      addLine(document, bodyFont, "Readiness score", report.score() + " / 100");
      addLine(document, bodyFont, "Readiness status", report.readinessStatus());
      addLine(document, bodyFont, "Release decision", report.releaseDecision());
      addLine(document, bodyFont, "Product label", report.productLabel());
      document.add(new Paragraph(" ", bodyFont));
      document.add(new Paragraph(
          "This report never declares legal certification or Regulation conformity.",
          bodyFont));

      document.add(new Paragraph(" ", bodyFont));
      document.add(new Paragraph("2. Dimension breakdown", headingFont));
      for (ReadinessDimensionScore dim : report.dimensions()) {
        addLine(document, bodyFont, dim.label(),
            dim.score() + "% (weight " + dim.weight() + ", " + dim.status() + ") — " + dim.summary());
      }

      document.add(new Paragraph(" ", bodyFont));
      document.add(new Paragraph("3. Structured gaps", headingFont));
      if (report.gaps().isEmpty()) {
        addLine(document, bodyFont, "Gaps", "(none)");
      } else {
        for (ReadinessGap gap : report.gaps()) {
          addLine(document, bodyFont, gap.code(),
              "[" + gap.severity() + "] " + gap.message()
                  + " — hint: " + gap.remediationHint());
        }
      }

      document.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IllegalStateException("Unable to render certification readiness PDF", e);
    }
  }

  public static String filename(UUID systemId, Instant generatedAt) {
    String date = generatedAt == null ? "unknown" : DATE.format(generatedAt);
    return "certification-readiness-" + systemId + "-" + date + ".pdf";
  }

  private static void addLine(Document document, Font font, String label, Object value)
      throws DocumentException {
    document.add(new Paragraph(label + ": " + (value == null ? "" : value), font));
  }

  private static String formatInstant(Instant instant) {
    return instant == null ? "" : DATETIME.format(instant);
  }
}
