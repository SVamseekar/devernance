package os.assurance.eu.api.framework;

import java.util.List;
import java.util.stream.Collectors;

/** RFC 4180 rows for exports people open in a spreadsheet. */
final class Csv {
  /** Byte-order mark, so spreadsheets read the file as UTF-8. */
  static final String BOM = "﻿";

  private Csv() {}

  static String row(List<String> cells) {
    return cells.stream().map(Csv::cell).collect(Collectors.joining(",")) + "\r\n";
  }

  /** Quotes as RFC 4180 requires and stops a spreadsheet treating recorded text as a formula. */
  static String cell(String value) {
    String text = value == null ? "" : value;
    if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
      return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    return text;
  }
}
