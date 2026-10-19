package os.assurance.eu.api.corpus;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FutureDutyGate implements CorpusForceLookup {
  private static final Pattern ANNEX_III = Pattern.compile("\\bAnnex III\\b");
  private static final Pattern ANNEX_I = Pattern.compile("\\bAnnex I\\b");
  private static final Pattern ARTICLE = Pattern.compile("Art\\.?\\s*(\\d+)");

  private final CorpusQueryService corpus;
  private final CorpusAsOf asOf;

  public FutureDutyGate(CorpusQueryService corpus, CorpusAsOf asOf) {
    this.corpus = corpus;
    this.asOf = asOf;
  }

  /** Reads the cached current corpus, not the provisions table, because the gate asks once per obligation. */
  @Override
  public boolean citesFutureDuty(String legalRefs) {
    return cites(
        legalRefs,
        corpus.current().provisions().stream()
            .map(row -> new Row(row.annex(), row.article(), row.forceFrom()))
            .toList(),
        asOf.today());
  }

  public record Row(String annex, String article, LocalDate forceFrom) {
  }

  static boolean cites(String legalRefs, List<Row> rows, LocalDate asOf) {
    if (legalRefs == null || legalRefs.isBlank() || rows.isEmpty()) {
      return false;
    }
    if (ANNEX_III.matcher(legalRefs).find() && annexFuture(rows, "annexIII", asOf)) {
      return true;
    }
    if (ANNEX_I.matcher(legalRefs).find() && annexFuture(rows, "annexI", asOf)) {
      return true;
    }
    if (ANNEX_III.matcher(legalRefs).find() || ANNEX_I.matcher(legalRefs).find()) {
      return false;
    }
    Matcher articles = ARTICLE.matcher(legalRefs);
    while (articles.find()) {
      String article = articles.group(1);
      boolean future = rows.stream().anyMatch(row ->
          article.equals(row.article())
              && (row.annex() == null || row.annex().isBlank())
              && row.forceFrom() != null
              && row.forceFrom().isAfter(asOf));
      if (future) {
        return true;
      }
    }
    return false;
  }

  private static boolean annexFuture(List<Row> rows, String annex, LocalDate asOf) {
    return rows.stream().anyMatch(row ->
        annex.equals(row.annex()) && row.forceFrom() != null && row.forceFrom().isAfter(asOf));
  }
}
