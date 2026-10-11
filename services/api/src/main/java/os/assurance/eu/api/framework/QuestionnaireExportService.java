package os.assurance.eu.api.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.assurance.eu.api.audit.AuditEvent;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.contract.DataContract;
import os.assurance.eu.api.contract.DataContractService;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.control.ControlStatus;
import os.assurance.eu.api.control.SystemControl;
import os.assurance.eu.api.eval.EvalRun;
import os.assurance.eu.api.eval.EvalRunRepository;
import os.assurance.eu.api.evidence.EvidenceDocument;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.sector.SectorPackRegistry;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.EvidencePackResponse;
import os.assurance.eu.api.system.EvidencePackService;
import os.assurance.eu.api.workflow.ApprovalStage;
import os.assurance.eu.api.workflow.ApprovalWorkflow;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import os.assurance.eu.api.workflow.StageStatus;

/**
 * Drafts answers to common AI-vendor due-diligence questions for one system. A template is filled only when every
 * fact it names is recorded for the system; otherwise the answer is {@link #MANUAL}. Nothing is inferred.
 */
@Service
public class QuestionnaireExportService {
  public static final String RESOURCE = "questionnaires/ai-vendor-core.json";
  public static final String MANUAL = "Not recorded in Assurance OS — answer manually.";
  public static final String DISCLAIMER = "Draft answers filled only from what is recorded in Assurance OS for this "
      + "system. Review every answer before you send it. This is not legal advice.";
  static final String NIST = "nist-ai-rmf";

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
  private static final Set<String> SYSTEM_FACTS = Set.of("name", "owner", "purpose", "riskClass", "riskBasis",
      "deploymentRegion", "vendorName", "modelName", "modelVersion", "dataSources", "sector", "decisionImpact",
      "affectedUsers");
  private static final Set<String> OTHER_FACTS = Set.of("contracts.names", "contracts.status", "eval.latestOn",
      "eval.dataset", "eval.modelVersion", "eval.decision", "approval.workflowCount", "approval.latestApprovedOn",
      "gate.decision", "audit.eventCount", "audit.latestOn", "audit.retainUntil", "nist.covered", "nist.partial",
      "nist.total", "pack.version");
  /** Top-level keys of the exported pack JSON; a {@code pack:} reference must start with one of them. */
  private static final Set<String> PACK_SECTIONS = Arrays.stream(EvidencePackResponse.class.getRecordComponents())
      .map(component -> component.getName())
      .filter(name -> !"conformity".equals(name))
      .collect(Collectors.toUnmodifiableSet());

  /** status is answered or manual; missing lists the facts a manual answer lacked. */
  public record Answer(String answer, String status, List<String> missing) {}

  public record AnswerRow(String id, String topic, String question, String answer, String status,
      List<Map<String, Object>> evidence, List<String> missing) {}

  private final Questionnaire questionnaire;
  private final AiSystemRepository systems;
  private final ControlService controls;
  private final EvidenceRepository evidence;
  private final AuditService audit;
  private final ApprovalWorkflowService approvals;
  private final DataContractService contracts;
  private final EvalRunRepository evalRuns;
  private final CrosswalkService crosswalks;
  private final Clock clock;

  public QuestionnaireExportService(ObjectMapper mapper, SectorPackRegistry sectorPacks, AiSystemRepository systems,
      ControlService controls, EvidenceRepository evidence, AuditService audit, ApprovalWorkflowService approvals,
      DataContractService contracts, EvalRunRepository evalRuns, CrosswalkService crosswalks, Clock clock) {
    this.questionnaire = load(mapper);
    validate(questionnaire, CrosswalkService.knownControlCodes(sectorPacks));
    this.systems = systems;
    this.controls = controls;
    this.evidence = evidence;
    this.audit = audit;
    this.approvals = approvals;
    this.contracts = contracts;
    this.evalRuns = evalRuns;
    this.crosswalks = crosswalks;
    this.clock = clock;
  }

  public static Questionnaire load(ObjectMapper mapper) {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      return mapper.readValue(in, Questionnaire.class);
    } catch (IOException e) {
      throw new IllegalStateException("Unreadable questionnaire " + RESOURCE, e);
    }
  }

  /** Throws {@link IllegalStateException} naming the first problem. Run at startup, never at request time. */
  public static void validate(Questionnaire questionnaire, Set<String> knownControlCodes) {
    String name = questionnaire.id();
    if (name == null || name.isBlank()) {
      throw new IllegalStateException("Questionnaire without an id");
    }
    if (questionnaire.questions().isEmpty()) {
      throw new IllegalStateException("Questionnaire " + name + " has no questions");
    }
    Set<String> ids = new HashSet<>();
    Set<String> topics = new HashSet<>();
    for (Questionnaire.Question q : questionnaire.questions()) {
      if (blank(q.id()) || blank(q.topic()) || blank(q.question()) || blank(q.answerTemplate())) {
        throw new IllegalStateException(
            "Questionnaire " + name + " has a question without id, topic, question or answerTemplate");
      }
      if (!ids.add(q.id()) || !topics.add(q.topic())) {
        throw new IllegalStateException("Questionnaire " + name + " repeats question " + q.id() + " or its topic");
      }
      for (String fact : facts(q.answerTemplate())) {
        requireKnownFact(name, q.id(), fact, knownControlCodes);
      }
      for (String ref : q.evidence()) {
        requireKnownReference(name, q.id(), ref, knownControlCodes);
      }
    }
  }

  private static void requireKnownFact(String name, String questionId, String fact, Set<String> knownControlCodes) {
    String[] parts = fact.split("\\.");
    boolean known = OTHER_FACTS.contains(fact)
        || parts.length == 2 && "system".equals(parts[0]) && SYSTEM_FACTS.contains(parts[1])
        || parts.length == 3 && "control".equals(parts[0]) && knownControlCodes.contains(parts[1])
            && "passedOn".equals(parts[2])
        || parts.length == 3 && "evidence".equals(parts[0]) && CrosswalkService.EVIDENCE_TYPES.contains(parts[1])
            && ("titles".equals(parts[2]) || "latestOn".equals(parts[2]));
    if (!known) {
      throw new IllegalStateException(
          "Questionnaire " + name + " question " + questionId + " uses unknown fact " + fact);
    }
  }

  private static void requireKnownReference(String name, String questionId, String ref,
      Set<String> knownControlCodes) {
    int colon = ref == null ? -1 : ref.indexOf(':');
    String kind = colon < 0 ? "" : ref.substring(0, colon);
    String target = colon < 0 ? "" : ref.substring(colon + 1);
    boolean known = switch (kind) {
      case "control" -> knownControlCodes.contains(target);
      case "evidence" -> CrosswalkService.EVIDENCE_TYPES.contains(target);
      case "pack" -> PACK_SECTIONS.contains(target.split("\\.")[0]);
      default -> false;
    };
    if (!known) {
      throw new IllegalStateException(
          "Questionnaire " + name + " question " + questionId + " has unknown evidence reference " + ref);
    }
  }

  /** The facts a template names, in the order they appear. */
  static List<String> facts(String template) {
    Set<String> out = new LinkedHashSet<>();
    Matcher matcher = PLACEHOLDER.matcher(template);
    while (matcher.find()) {
      out.add(matcher.group(1));
    }
    return List.copyOf(out);
  }

  /** Fills the template in one pass, or returns {@link #MANUAL} with the facts that are not recorded. */
  public static Answer answer(Questionnaire.Question question, Map<String, String> facts) {
    List<String> missing = facts(question.answerTemplate()).stream()
        .filter(fact -> blank(facts.get(fact)))
        .toList();
    if (!missing.isEmpty()) {
      return new Answer(MANUAL, "manual", missing);
    }
    String filled = PLACEHOLDER.matcher(question.answerTemplate())
        .replaceAll(match -> Matcher.quoteReplacement(facts.get(match.group(1))));
    return new Answer(filled, "answered", List.of());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> export(UUID systemId) {
    AiSystem system = systems.findById(systemId)
        .orElseThrow(() -> new IllegalArgumentException("AI system not found"));
    List<AnswerRow> rows = rows(system);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("questionnaire", questionnaire.id());
    out.put("version", questionnaire.version());
    out.put("title", questionnaire.title());
    out.put("systemId", system.id());
    out.put("systemName", system.name());
    out.put("generatedAt", clock.instant().toString());
    out.put("disclaimer", DISCLAIMER);
    out.put("answered", (int) rows.stream().filter(row -> "answered".equals(row.status())).count());
    out.put("manual", (int) rows.stream().filter(row -> "manual".equals(row.status())).count());
    out.put("answers", rows);
    return out;
  }

  @Transactional(readOnly = true)
  public String exportCsv(UUID systemId) {
    AiSystem system = systems.findById(systemId)
        .orElseThrow(() -> new IllegalArgumentException("AI system not found"));
    return toCsv(rows(system));
  }

  public String questionnaireId() {
    return questionnaire.id();
  }

  private List<AnswerRow> rows(AiSystem system) {
    List<SystemControl> systemControls = controls.listForSystem(system.id());
    List<EvidenceDocument> indexed = evidence.findDocumentsBySystemId(system.id()).stream()
        .filter(document -> CrosswalkService.isIndexed(document.ingestionStatus()))
        .sorted(Comparator.comparing(EvidenceDocument::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
    Map<String, String> facts = facts(system, systemControls, indexed);
    List<AnswerRow> rows = new ArrayList<>();
    for (Questionnaire.Question q : questionnaire.questions()) {
      Answer answer = answer(q, facts);
      rows.add(new AnswerRow(q.id(), q.topic(), q.question(), answer.answer(), answer.status(),
          references(q, systemControls, indexed), answer.missing()));
    }
    return rows;
  }

  private Map<String, String> facts(AiSystem system, List<SystemControl> systemControls,
      List<EvidenceDocument> indexed) {
    Map<String, String> facts = new HashMap<>();
    put(facts, "system.name", system.name());
    put(facts, "system.owner", system.owner());
    put(facts, "system.purpose", system.purpose());
    put(facts, "system.riskClass", system.riskClass() == null ? null : system.riskClass().name());
    put(facts, "system.riskBasis", system.riskBasis());
    put(facts, "system.deploymentRegion", system.deploymentRegion());
    put(facts, "system.vendorName", system.vendorName());
    put(facts, "system.modelName", system.modelName());
    put(facts, "system.modelVersion", system.modelVersion());
    put(facts, "system.dataSources", join(system.dataSources()));
    put(facts, "system.sector", system.sector());
    put(facts, "system.decisionImpact", system.decisionImpact());
    put(facts, "system.affectedUsers", join(system.affectedUsers()));
    put(facts, "gate.decision", system.releaseDecision() == null ? null : system.releaseDecision().name());
    put(facts, "pack.version", EvidencePackService.PACK_VERSION);

    Map<String, ControlStatus> statuses = new HashMap<>();
    for (SystemControl control : systemControls) {
      statuses.merge(control.controlCode(), control.status(),
          (a, b) -> a == ControlStatus.PASS || b == ControlStatus.PASS ? ControlStatus.PASS : a);
      if (control.status() == ControlStatus.PASS) {
        put(facts, "control." + control.controlCode() + ".passedOn", date(control.updatedAt()));
      }
    }
    Map<String, List<EvidenceDocument>> byType = indexed.stream()
        .collect(Collectors.groupingBy(EvidenceDocument::type, LinkedHashMap::new, Collectors.toList()));
    byType.forEach((type, documents) -> {
      put(facts, "evidence." + type + ".titles",
          join(documents.stream().map(EvidenceDocument::title).toList()));
      put(facts, "evidence." + type + ".latestOn", date(documents.get(documents.size() - 1).createdAt()));
    });

    List<DataContract> dataContracts = contracts.listContracts(system.id());
    if (!dataContracts.isEmpty()) {
      put(facts, "contracts.names", join(dataContracts.stream()
          .map(contract -> contract.name() + " (version " + contract.version() + ")").toList()));
      put(facts, "contracts.status",
          system.dataContractStatus() == null ? null : system.dataContractStatus().name());
    }

    evalRuns.findLatestCompleted(system.id()).ifPresent(run -> evalFacts(facts, run));

    List<ApprovalWorkflow> workflows = approvals.listBySystemId(system.id());
    if (!workflows.isEmpty()) {
      put(facts, "approval.workflowCount", String.valueOf(workflows.size()));
      workflows.stream().flatMap(workflow -> workflow.stages().stream())
          .filter(stage -> stage.status() == StageStatus.APPROVED)
          .map(ApprovalStage::actedAt)
          .filter(Objects::nonNull)
          .max(Comparator.naturalOrder())
          .ifPresent(at -> put(facts, "approval.latestApprovedOn", date(at)));
    }

    List<AuditEvent> events = audit.findBySystemId(system.id());
    if (!events.isEmpty()) {
      put(facts, "audit.eventCount", String.valueOf(events.size()));
      events.stream().map(AuditEvent::createdAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
          .ifPresent(at -> put(facts, "audit.latestOn", date(at)));
      events.stream().map(AuditEvent::retainUntil).filter(Objects::nonNull).min(Comparator.naturalOrder())
          .ifPresent(at -> put(facts, "audit.retainUntil", date(at)));
    }

    crosswalks.find(NIST).ifPresent(crosswalk -> {
      Set<String> indexedTypes = byType.keySet();
      List<CrosswalkService.ItemStatus> coverage = CrosswalkService.coverage(crosswalk, statuses, indexedTypes);
      long covered = coverage.stream().filter(item -> "covered".equals(item.status())).count();
      long partial = coverage.stream().filter(item -> "partial".equals(item.status())).count();
      // With nothing mapped yet there is no alignment to describe, so the answer stays manual.
      if (covered + partial > 0) {
        put(facts, "nist.covered", String.valueOf(covered));
        put(facts, "nist.partial", String.valueOf(partial));
        put(facts, "nist.total", String.valueOf(coverage.size()));
      }
    });
    return facts;
  }

  private static void evalFacts(Map<String, String> facts, EvalRun run) {
    put(facts, "eval.latestOn", date(run.completedAt()));
    put(facts, "eval.dataset", run.dataset());
    put(facts, "eval.modelVersion", run.modelVersion());
    put(facts, "eval.decision", run.releaseDecision() == null ? null : run.releaseDecision().name());
  }

  private static List<Map<String, Object>> references(Questionnaire.Question question,
      List<SystemControl> systemControls, List<EvidenceDocument> indexed) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String ref : question.evidence()) {
      int colon = ref.indexOf(':');
      String kind = ref.substring(0, colon);
      String target = ref.substring(colon + 1);
      switch (kind) {
        case "control" -> {
          Map<String, Object> entry = entry("control", target);
          SystemControl control = systemControls.stream()
              .filter(c -> target.equals(c.controlCode())).findFirst().orElse(null);
          entry.put("status", control == null ? "NOT_ATTACHED" : control.status().name());
          if (control != null && control.updatedAt() != null) {
            entry.put("updatedAt", control.updatedAt().toString());
          }
          out.add(entry);
        }
        case "evidence" -> {
          List<EvidenceDocument> documents = indexed.stream().filter(d -> target.equals(d.type())).toList();
          if (documents.isEmpty()) {
            Map<String, Object> entry = entry("document", target);
            entry.put("status", "none_indexed");
            out.add(entry);
          }
          for (EvidenceDocument document : documents) {
            Map<String, Object> entry = entry("document", target);
            entry.put("status", "indexed");
            entry.put("id", document.id().toString());
            entry.put("title", document.title());
            entry.put("sha256", document.checksum());
            if (document.createdAt() != null) {
              entry.put("createdAt", document.createdAt().toString());
            }
            out.add(entry);
          }
        }
        default -> out.add(entry("pack", target));
      }
    }
    return out;
  }

  private static Map<String, Object> entry(String kind, String ref) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("kind", kind);
    entry.put("ref", ref);
    return entry;
  }

  /** RFC 4180 with a UTF-8 byte-order mark (so spreadsheets read the dash in {@link #MANUAL} correctly). */
  public static String toCsv(List<AnswerRow> rows) {
    StringBuilder csv = new StringBuilder(Csv.BOM)
        .append(Csv.row(List.of("id", "topic", "question", "answer", "status", "evidence")));
    for (AnswerRow row : rows) {
      csv.append(Csv.row(List.of(row.id(), row.topic(), row.question(), row.answer(), row.status(),
          row.evidence().stream().map(QuestionnaireExportService::label).collect(Collectors.joining("; ")))));
    }
    return csv.toString();
  }

  private static String label(Map<String, Object> reference) {
    String kind = String.valueOf(reference.get("kind"));
    String ref = String.valueOf(reference.get("ref"));
    return switch (kind) {
      case "control" -> "control " + ref + " (" + reference.get("status") + ")";
      case "document" -> reference.get("title") == null
          ? ref + ": none indexed"
          : ref + ": " + reference.get("title")
              + (reference.get("sha256") == null ? "" : " [sha256 " + reference.get("sha256") + "]");
      default -> "pack " + ref;
    };
  }

  private static void put(Map<String, String> facts, String key, String value) {
    if (!blank(value)) {
      facts.put(key, value.replaceAll("\\s+", " ").trim());
    }
  }

  private static String join(List<String> values) {
    return values == null ? null : values.stream().filter(v -> !blank(v)).map(String::trim)
        .collect(Collectors.joining("; "));
  }

  private static String date(Instant at) {
    return at == null ? null : LocalDate.ofInstant(at, ZoneOffset.UTC).toString();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
