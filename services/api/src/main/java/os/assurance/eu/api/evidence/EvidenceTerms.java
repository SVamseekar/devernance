package os.assurance.eu.api.evidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Search terms of a text: lower-cased words without stop words, with plural and verb endings removed so
 * "approvals" matches "approval" and "tested" matches "testing". Postgres uses its own English stemmer for the
 * same purpose; this one serves the in-memory index and the choice of the quoted sentence.
 */
final class EvidenceTerms {
  private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Set<String> STOP_WORDS = Set.of(
      "a", "about", "above", "after", "again", "all", "also", "am", "an", "and", "any", "are", "as", "at", "be",
      "been", "before", "being", "below", "between", "both", "but", "by", "can", "could", "did", "do", "does",
      "doing", "down", "during", "each", "few", "for", "from", "further", "had", "has", "have", "having", "he",
      "her", "here", "hers", "him", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just",
      "me", "more", "most", "my", "no", "nor", "not", "now", "of", "off", "on", "once", "only", "or", "other",
      "our", "ours", "out", "over", "own", "same", "she", "should", "so", "some", "such", "than", "that", "the",
      "their", "theirs", "them", "then", "there", "these", "they", "this", "those", "through", "to", "too",
      "under", "until", "up", "very", "was", "we", "were", "what", "when", "where", "which", "while", "who",
      "whom", "why", "will", "with", "would", "you", "your", "yours");

  private EvidenceTerms() {
  }

  /** Every term of the text, in order, repeats kept. */
  static List<String> of(String text) {
    List<String> terms = new ArrayList<>();
    for (String word : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
      if (word.length() < 2 || STOP_WORDS.contains(word)) {
        continue;
      }
      terms.add(stem(word));
    }
    return terms;
  }

  static Set<String> distinct(String text) {
    return new LinkedHashSet<>(of(text));
  }

  static String stem(String word) {
    if (word.length() <= 3 || !Character.isLetter(word.charAt(word.length() - 1))) {
      return word;
    }
    if (word.endsWith("ies") && word.length() > 4) {
      return word.substring(0, word.length() - 3) + "y";
    }
    if (word.endsWith("ing") && word.length() > 5) {
      return word.substring(0, word.length() - 3);
    }
    if (word.endsWith("ed") && word.length() > 4) {
      return word.substring(0, word.length() - 2);
    }
    if (word.endsWith("es") && word.length() > 4 && "sxz".indexOf(word.charAt(word.length() - 3)) >= 0) {
      return word.substring(0, word.length() - 2);
    }
    if (word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is")) {
      return word.substring(0, word.length() - 1);
    }
    return word;
  }
}
