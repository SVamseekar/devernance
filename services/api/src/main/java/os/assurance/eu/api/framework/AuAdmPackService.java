package os.assurance.eu.api.framework;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.control.ControlStatus;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.RiskClass;

/**
 * Australian Privacy Act automated-decision transparency (APP 1.7-1.9, from 10 December 2026): lists which
 * registered systems need describing in the privacy policy and drafts wording only from recorded facts.
 */
@Service
public class AuAdmPackService {
  public static final String OBLIGATION_START = "2026-12-10";
  /** Privacy and Other Legislation Amendment Act 2024 (No. 128, 2024), Schedule 1 Part 15. */
  public static final String SOURCE = "https://www.legislation.gov.au/C2024A00128/latest/text";
  /** OAIC issues paper reproducing APP 1.7-1.9 (Attachment A). */
  public static final String GUIDANCE = "https://www.oaic.gov.au/__data/assets/pdf_file/0027/263925/ADM-Issues-Paper.pdf";
  public static final String DISCLAIMER = "Draft inputs for your privacy policy, built only from what is recorded in "
      + "Assurance OS. This is not legal advice; have your privacy counsel review every statement.";
  public static final String DRAFT_PREFIX = "Draft for review by your privacy counsel:";

  /** APP 1.8: the information the privacy policy must contain (official text). */
  public static final List<Map<String, String>> ELEMENTS = List.of(
      Map.of("clause", "APP 1.8(a)",
          "text", "the kinds of personal information used in the operation of such computer programs"),
      Map.of("clause", "APP 1.8(b)",
          "text", "the kinds of such decisions made solely by the operation of such computer programs"),
      Map.of("clause", "APP 1.8(c)",
          "text", "the kinds of such decisions for which a thing, that is substantially and directly related to "
              + "making the decision, is done by the operation of such computer programs"));

  public record SystemEntry(
      UUID systemId, String name, String status, List<String> missing, String reason, String draftStatement) {}

  private final AiSystemRepository systems;
  private final ControlService controls;
  private final Clock clock;

  public AuAdmPackService(AiSystemRepository systems, ControlService controls, Clock clock) {
    this.systems = systems;
    this.controls = controls;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> build() {
    List<SystemEntry> entries = new ArrayList<>();
    for (AiSystem system : systems.findAll()) {
      boolean oversight = controls.listForSystem(system.id()).stream()
          .anyMatch(c -> "HUMAN_OVERSIGHT".equals(c.controlCode()) && c.status() == ControlStatus.PASS);
      entries.add(classify(system, oversight));
    }
    Map<String, Object> pack = new LinkedHashMap<>();
    pack.put("generatedAt", clock.instant().toString());
    pack.put("obligationStart", OBLIGATION_START);
    pack.put("source", GUIDANCE);
    pack.put("legislation", SOURCE);
    pack.put("disclaimer", DISCLAIMER);
    pack.put("elements", ELEMENTS);
    pack.put("systems", entries);
    return pack;
  }

  /** include, needs_input (with the missing fields), or not_applicable. Never drafts from missing facts. */
  public static SystemEntry classify(AiSystem system, boolean humanOversightPass) {
    if (system.riskClass() == RiskClass.MINIMAL && system.affectedUsers().isEmpty()) {
      return new SystemEntry(system.id(), system.name(), "not_applicable", List.of(),
          "Minimal-risk system with no affected individuals recorded.", null);
    }
    List<String> missing = new ArrayList<>();
    if (blank(system.purpose())) {
      missing.add("purpose");
    }
    if (blank(system.decisionImpact())) {
      missing.add("decisionImpact");
    }
    if (system.affectedUsers().isEmpty()) {
      missing.add("affectedUsers");
    }
    if (system.dataSources().isEmpty()) {
      missing.add("dataSources");
    }
    if (!missing.isEmpty()) {
      return new SystemEntry(system.id(), system.name(), "needs_input", List.copyOf(missing),
          "Record the missing fields on the system before drafting.", null);
    }
    String verb = humanOversightPass
        ? "substantially and directly supports"
        : "makes or substantially and directly supports (confirm which)";
    String draft = DRAFT_PREFIX + " We use " + system.name() + " to " + sentenceFragment(system.purpose())
        + ". It " + verb + " decisions about " + system.decisionImpact().trim() + " for "
        + String.join(", ", system.affectedUsers()) + ". It uses the following kinds of personal information: "
        + String.join(", ", system.dataSources()) + ". A person reviews these decisions: "
        + (humanOversightPass ? "yes" : "to be confirmed") + ".";
    return new SystemEntry(system.id(), system.name(), "include", List.of(),
        "Decisions about individuals using personal information are recorded for this system.", draft);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String sentenceFragment(String purpose) {
    String text = purpose.trim();
    while (text.endsWith(".")) {
      text = text.substring(0, text.length() - 1);
    }
    return text.isEmpty() ? text : text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
  }
}
