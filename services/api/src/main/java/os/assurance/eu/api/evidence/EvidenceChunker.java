package os.assurance.eu.api.evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Splits extracted text into retrieval chunks of whole sentences.
 *
 * <p>A chunk holds about {@value #TARGET_WORDS} words and never more than {@value #MAX_WORDS}, so it fits in the
 * 256 tokens the sentence-embedding model reads; consecutive chunks share about a quarter of their words, the
 * overlap that retrieval studies on long PDFs found best. Chunks of a paged document (a PDF) stay inside one page
 * and are cited by page, so a reader can open the source at the passage.
 */
@Component
public class EvidenceChunker {
  static final int TARGET_WORDS = 120;
  static final int MAX_WORDS = 160;
  static final int OVERLAP_WORDS = 30;
  /** evidence_chunks.content holds 8,192 characters; a run of very long tokens is cut to fit. */
  static final int MAX_CHARACTERS = 7_800;
  private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?;:])\\s+|\\n\\s*\\n");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public List<ChunkDraft> chunk(String text) {
    String[] pages = text.split(String.valueOf(TextExtractionService.PAGE_BREAK), -1);
    boolean paged = pages.length > 1;
    List<ChunkDraft> chunks = new ArrayList<>();
    int ordinal = 1;
    for (int page = 0; page < pages.length; page++) {
      for (String window : windows(sentences(pages[page]))) {
        for (int start = 0; start < window.length(); start += MAX_CHARACTERS) {
          String ref = paged ? "p. " + (page + 1) : "Section " + ordinal;
          chunks.add(new ChunkDraft(ref, window.substring(start, Math.min(window.length(), start + MAX_CHARACTERS))));
          ordinal++;
        }
      }
    }
    if (chunks.isEmpty()) {
      return List.of(new ChunkDraft("Section 1", "No extractable text was found."));
    }
    return chunks;
  }

  /** Sentences (or paragraph-separated lines) of a page; an over-long run such as a table is cut by words. */
  static List<List<String>> sentences(String page) {
    List<List<String>> out = new ArrayList<>();
    for (String sentence : SENTENCE_END.split(page)) {
      String normalized = WHITESPACE.matcher(sentence).replaceAll(" ").strip();
      if (normalized.isEmpty()) {
        continue;
      }
      List<String> words = List.of(normalized.split(" "));
      for (int start = 0; start < words.size(); start += MAX_WORDS) {
        out.add(words.subList(start, Math.min(words.size(), start + MAX_WORDS)));
      }
    }
    return out;
  }

  /** Packs sentences into chunks; each chunk after the first starts with the last sentences of the one before. */
  private static List<String> windows(List<List<String>> sentences) {
    List<String> out = new ArrayList<>();
    int start = 0;
    while (start < sentences.size()) {
      int end = start;
      int words = 0;
      while (end < sentences.size()
          && (end == start || words + sentences.get(end).size() <= MAX_WORDS)
          && words < TARGET_WORDS) {
        words += sentences.get(end).size();
        end++;
      }
      out.add(join(sentences.subList(start, end)));
      if (end >= sentences.size()) {
        break;
      }
      // Step back over whole sentences worth up to OVERLAP_WORDS, but always move forward.
      int next = end;
      int overlap = 0;
      while (next - 1 > start && overlap + sentences.get(next - 1).size() <= OVERLAP_WORDS) {
        overlap += sentences.get(next - 1).size();
        next--;
      }
      start = next;
    }
    return out;
  }

  private static String join(List<List<String>> sentences) {
    StringBuilder builder = new StringBuilder();
    for (List<String> sentence : sentences) {
      if (!builder.isEmpty()) {
        builder.append(' ');
      }
      builder.append(String.join(" ", sentence));
    }
    return builder.toString();
  }

  public record ChunkDraft(String sectionRef, String content) {
  }
}
