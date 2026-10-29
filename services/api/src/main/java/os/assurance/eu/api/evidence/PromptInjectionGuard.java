package os.assurance.eu.api.evidence;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Drops document lines that try to instruct the model (OWASP LLM01 indirect injection). Patterns match commands,
 * not topics: vendor transparency notes and model cards talk about "system prompts" and "developer messages" as
 * concepts, and dropping those lines would also drop legitimate evidence. Filtering is a backstop only; answers
 * quote retrieved text and never follow it.
 */
@Component
public class PromptInjectionGuard {
  private static final List<Pattern> DOCUMENT_INJECTION_PATTERNS = List.of(
      Pattern.compile("\\bignore (all |any |the )?(previous|prior|above|earlier)\\b"),
      Pattern.compile("\\bdisregard (all |any |the )?(previous|prior|above|earlier) (instructions|rules|messages)\\b"),
      Pattern.compile("\\b(reveal|print|show|repeat|output|leak) (me )?(the |your )?(hidden |secret |full )?(system prompt|developer message|instructions)\\b"),
      Pattern.compile("\\b(you are|you're) now (in |a |an )?(developer|god|jailbreak|dan) mode\\b"),
      Pattern.compile("\\breveal secret"),
      Pattern.compile("\\bdisable citation"),
      Pattern.compile("\\bdo not cite (any |the |your )?(sources|documents|evidence|citations)\\b"),
      Pattern.compile("\\bbypass (the |any |all )?(policy|policies|safety|guardrails)\\b"),
      Pattern.compile("\\boverride (the |your |all |any )?(previous |prior )?instruction"));

  private static final String ZERO_WIDTH_CHARACTERS = "[\\u200B-\\u200F\\uFEFF]";

  public SanitizedText sanitizeDocumentText(String text) {
    List<String> lines = text.lines().toList();
    List<String> safeLines = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (looksLikeInjection(normalize(line))) {
        removed.add(line.strip());
        continue;
      }
      if (i + 1 < lines.size()) {
        String next = lines.get(i + 1);
        if (pairIntroducesNewPattern(line, next)) {
          removed.add(line.strip());
          if (!looksLikeInjection(normalize(next))) {
            removed.add(next.strip());
            i++;
          }
          continue;
        }
      }
      safeLines.add(line);
    }
    String sanitized = String.join("\n", safeLines).strip();

    String collapsedWholeText = normalize(text).replaceAll("\\s+", " ");
    if (looksLikeInjection(collapsedWholeText) && !removed.isEmpty()) {
      // A phrase was already caught above — no extra action needed for that case.
    } else if (looksLikeInjection(collapsedWholeText)) {
      return new SanitizedText("Document text removed by prompt-injection guard.", List.of(text.strip()));
    }

    return new SanitizedText(sanitized.isBlank() ? "Document text removed by prompt-injection guard." : sanitized, removed);
  }

  public String sanitizeQuestion(String question) {
    return question == null ? "" : question.replaceAll("\\s+", " ").strip();
  }

  private String normalize(String input) {
    String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
    return normalized
        .replaceAll(ZERO_WIDTH_CHARACTERS, " ")
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT)
        .strip();
  }

  private boolean looksLikeInjection(String normalizedLowercaseText) {
    return DOCUMENT_INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalizedLowercaseText).find());
  }

  private boolean pairIntroducesNewPattern(String line, String next) {
    String normalizedLine = normalize(line);
    String normalizedNext = normalize(next);
    String pair = normalizedLine + " " + normalizedNext;
    if (!looksLikeInjection(pair)) {
      return false;
    }
    if (!looksLikeInjection(normalizedLine) && !looksLikeInjection(normalizedNext)) {
      return true;
    }
    for (Pattern pattern : DOCUMENT_INJECTION_PATTERNS) {
      if (pattern.matcher(pair).find() && !pattern.matcher(normalizedLine).find()
          && !pattern.matcher(normalizedNext).find()) {
        return true;
      }
    }
    return false;
  }

  public record SanitizedText(String text, List<String> removedLines) {
  }
}
