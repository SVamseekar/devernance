package os.assurance.eu.api.system;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import os.assurance.eu.api.conformity.ConformityService;
import os.assurance.eu.api.control.ControlEntity;
import os.assurance.eu.api.control.ControlJpaRepository;
import os.assurance.eu.api.control.ControlStatus;
import os.assurance.eu.api.control.SystemControlEntity;
import os.assurance.eu.api.control.SystemControlJpaRepository;
import os.assurance.eu.api.determination.Applicability;
import os.assurance.eu.api.determination.DeterminationObligation;
import os.assurance.eu.api.determination.DeterminationObligationEntity;
import os.assurance.eu.api.determination.DeterminationObligationJpaRepository;
import os.assurance.eu.api.determination.DeterminationRunEntity;
import os.assurance.eu.api.corpus.CorpusForceLookup;
import os.assurance.eu.api.determination.DeterminationRunJpaRepository;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReleaseGateService {
  private static final int EVIDENCE_PASS_THRESHOLD = 82;
  private static final int EVAL_PASS_THRESHOLD = 85;
  private static final int EVAL_HARD_BLOCK_THRESHOLD = 78;

  private final SystemControlJpaRepository systemControls;
  private final ControlJpaRepository controls;
  private final TenantContext tenantContext;
  private final DeterminationRunJpaRepository determinationRuns;
  private final DeterminationObligationJpaRepository determinationObligations;
  private final ConformityService conformityService;
  private final CorpusForceLookup corpusForceLookup;
  private final GateControlSource gateControls;
  private final List<ReleaseGateSignals> signalSources;
  private final boolean evidenceDriven;

  @Autowired
  public ReleaseGateService(
      SystemControlJpaRepository systemControls,
      ControlJpaRepository controls,
      TenantContext tenantContext,
      DeterminationRunJpaRepository determinationRuns,
      DeterminationObligationJpaRepository determinationObligations,
      ConformityService conformityService,
      CorpusForceLookup corpusForceLookup,
      GateControlSource gateControls,
      List<ReleaseGateSignals> signalSources,
      @org.springframework.beans.factory.annotation.Value("${assurance.gate.manual-inputs:true}") boolean manualInputs) {
    this.systemControls = systemControls;
    this.controls = controls;
    this.tenantContext = tenantContext;
    this.determinationRuns = determinationRuns;
    this.determinationObligations = determinationObligations;
    this.conformityService = conformityService;
    this.corpusForceLookup = corpusForceLookup;
    this.gateControls = gateControls;
    this.signalSources = signalSources == null ? List.of() : List.copyOf(signalSources);
    this.evidenceDriven = !manualInputs;
  }

  public ReleaseGateService(
      SystemControlJpaRepository systemControls,
      ControlJpaRepository controls,
      TenantContext tenantContext,
      DeterminationRunJpaRepository determinationRuns,
      DeterminationObligationJpaRepository determinationObligations,
      ConformityService conformityService,
      CorpusForceLookup corpusForceLookup) {
    this(
        systemControls,
        controls,
        tenantContext,
        determinationRuns,
        determinationObligations,
        conformityService,
        corpusForceLookup,
        null,
        List.of(),
        true);
  }

  /** Test/local constructor without control lookup. */
  public ReleaseGateService() {
    this.systemControls = null;
    this.controls = null;
    this.tenantContext = null;
    this.determinationRuns = null;
    this.determinationObligations = null;
    this.conformityService = null;
    this.corpusForceLookup = null;
    this.gateControls = null;
    this.signalSources = List.of();
    this.evidenceDriven = false;
  }

  public ReleaseGateResponse calculate(AiSystem system) {
    List<String> extra = new ArrayList<>();
    extra.addAll(loadControlBlockers(system.id()));
    extra.addAll(loadDeterminationHardBlockers(system.id()));
    return calculate(system, extra);
  }

  public ReleaseGateResponse calculate(AiSystem system, List<String> controlBlockers) {
    List<String> blockers = new ArrayList<>();
    List<String> reviewReasons = new ArrayList<>();
    if (system.riskClass() == RiskClass.PROHIBITED) {
      blockers.add("Risk class is prohibited for release");
    }
    // The oversight gap opened at classification is answered by compliance passing the HUMAN_OVERSIGHT control.
    boolean oversightAnswered = humanOversightPassed(system.id());
    if (system.riskClass() == RiskClass.HIGH && hasOversightGap(system.openGaps()) && !oversightAnswered) {
      blockers.add("High-risk system is missing required human oversight evidence");
    }
    boolean noEvalYet = evidenceDriven && system.evalScore() == 0;
    if (noEvalYet && system.riskClass() == RiskClass.HIGH) {
      blockers.add("No completed eval run for a high-risk system");
    } else if (!noEvalYet && system.evalScore() < EVAL_HARD_BLOCK_THRESHOLD) {
      blockers.add("Eval score is below hard release threshold");
    }
    if (system.dataContractStatus() == DataContractStatus.BREACH) {
      blockers.add("Data contract breach is open");
    }
    if (controlBlockers != null) {
      for (String controlBlocker : controlBlockers) {
        addOnce(blockers, controlBlocker);
      }
    }
    for (ReleaseGateSignals.Signal signal : signals(system)) {
      addOnce(signal.level() == ReleaseGateSignals.Level.BLOCK ? blockers : reviewReasons, signal.message());
    }

    if (system.evidenceCoverage() < EVIDENCE_PASS_THRESHOLD) {
      reviewReasons.add("Evidence coverage " + system.evidenceCoverage() + "% is below " + EVIDENCE_PASS_THRESHOLD + "%");
    }
    if (!noEvalYet && system.evalScore() >= EVAL_HARD_BLOCK_THRESHOLD && system.evalScore() < EVAL_PASS_THRESHOLD) {
      reviewReasons.add("Eval score " + system.evalScore() + " is below " + EVAL_PASS_THRESHOLD);
    }
    if (system.dataContractStatus() == DataContractStatus.WARNING) {
      reviewReasons.add("Data contract has an open warning, or no data contract is recorded");
    }
    for (String gap : system.openGaps()) {
      if (oversightAnswered && gap.toLowerCase(Locale.ROOT).contains("oversight")) {
        continue;
      }
      addOnce(reviewReasons, "Open gap: " + gap);
    }
    if (system.riskClass() == RiskClass.HIGH) {
      for (String flag : loadConformityReviewFlags(system.id(), system.riskClass())) {
        addOnce(reviewReasons, describeConformityFlag(flag));
      }
    }

    if (!blockers.isEmpty()) {
      return finish(system, ReleaseDecision.BLOCKED, blockers, reviewReasons);
    }
    return finish(
        system,
        reviewReasons.isEmpty() ? ReleaseDecision.PASS : ReleaseDecision.REVIEW,
        List.of(),
        reviewReasons);
  }

  private ReleaseGateResponse finish(
      AiSystem system, ReleaseDecision decision, List<String> blockers, List<String> reviewReasons) {
    List<GateControl> controls = gateControls == null || system.id() == null
        ? List.of()
        : gateControls.acceptedLinks(system.id());
    ReleaseDecision adjusted = ControlModePolicy.apply(decision, controls);
    List<String> reasons = new ArrayList<>(reviewReasons);
    if (decision == ReleaseDecision.PASS && adjusted == ReleaseDecision.REVIEW) {
      reasons.add("An accepted control link requires review or sign-off");
    }
    return new ReleaseGateResponse(
        system.id(),
        adjusted,
        ControlModePolicy.blockers(adjusted, blockers, controls),
        controls,
        reasons);
  }

  private List<ReleaseGateSignals.Signal> signals(AiSystem system) {
    if (system.id() == null || signalSources.isEmpty()) {
      return List.of();
    }
    List<ReleaseGateSignals.Signal> out = new ArrayList<>();
    for (ReleaseGateSignals source : signalSources) {
      out.addAll(source.signals(system));
    }
    return out;
  }

  private static void addOnce(List<String> list, String message) {
    if (message != null && !message.isBlank() && !list.contains(message)) {
      list.add(message);
    }
  }

  static String describeConformityFlag(String flag) {
    if (flag.startsWith("ANNEX_IV_INCOMPLETE:")) {
      return "Annex IV section " + flag.substring("ANNEX_IV_INCOMPLETE:".length())
          + " is missing from the conformity dossier";
    }
    if ("FRIA_NOT_RECORDED".equals(flag)) {
      return "Fundamental rights impact assessment (FRIA) is not recorded or waived";
    }
    return flag;
  }

  private List<String> loadControlBlockers(UUID systemId) {
    if (systemControls == null || controls == null || tenantContext == null || systemId == null) {
      return List.of();
    }
    Map<UUID, String> codes = controls.findAll().stream()
        .map(ControlEntity::toDomain)
        .collect(Collectors.toMap(c -> c.id(), c -> c.code(), (a, b) -> a));
    return systemControls
        .findAllByTenantIdAndSystemIdAndStatus(
            tenantContext.tenantId(), systemId, ControlStatus.BLOCKED)
        .stream()
        .map(SystemControlEntity::controlId)
        .map(id -> "CONTROL:" + codes.getOrDefault(id, "UNKNOWN"))
        .distinct()
        .toList();
  }

  private List<String> loadDeterminationHardBlockers(UUID systemId) {
    if (determinationRuns == null || determinationObligations == null || tenantContext == null
        || systemId == null || systemControls == null || controls == null) {
      return List.of();
    }
    DeterminationRunEntity run = determinationRuns
        .findFirstByTenantIdAndSystemIdOrderByCreatedAtDesc(tenantContext.tenantId(), systemId)
        .orElse(null);
    if (run == null) {
      return List.of();
    }
    Map<String, ControlStatus> controlStatusByCode = controlStatusIndex(systemId);
    List<String> blockers = new ArrayList<>();
    for (DeterminationObligationEntity entity :
        determinationObligations.findAllByRunIdOrderByRuleCodeAsc(run.id())) {
      DeterminationObligation item = entity.toDomain();
      if (item.applicability() != Applicability.APPLICABLE) {
        continue;
      }
      if (corpusForceLookup != null && corpusForceLookup.citesFutureDuty(item.legalRefs())) {
        continue;
      }
      String code = item.ruleCode() == null ? "" : item.ruleCode();
      if (code.startsWith("PROHIBITED_")) {
        blockers.add("PROHIBITED_PRACTICE:" + code);
        continue;
      }
      boolean highOrMedium = "HIGH".equalsIgnoreCase(item.severity())
          || "MEDIUM".equalsIgnoreCase(item.severity());
      if (!highOrMedium || item.controlCodes() == null) {
        continue;
      }
      for (String controlCode : item.controlCodes()) {
        if (controlCode == null || controlCode.isBlank()) {
          continue;
        }
        ControlStatus status = controlStatusByCode.get(controlCode.toUpperCase(Locale.ROOT));
        if (status != ControlStatus.PASS) {
          String marker = "OBLIGATION_UNMET:" + code + ":" + controlCode;
          if (!blockers.contains(marker)) {
            blockers.add(marker);
          }
        }
      }
    }
    return blockers;
  }

  private Map<String, ControlStatus> controlStatusIndex(UUID systemId) {
    Map<UUID, String> codes = controls.findAll().stream()
        .map(ControlEntity::toDomain)
        .collect(Collectors.toMap(c -> c.id(), c -> c.code(), (a, b) -> a));
    Map<String, ControlStatus> index = new LinkedHashMap<>();
    for (SystemControlEntity row : systemControls.findAllByTenantIdAndSystemIdOrderByUpdatedAtDesc(
        tenantContext.tenantId(), systemId)) {
      String code = codes.get(row.controlId());
      if (code != null) {
        index.putIfAbsent(code.toUpperCase(Locale.ROOT), row.status());
      }
    }
    return index;
  }

  private List<String> loadConformityReviewFlags(UUID systemId, RiskClass riskClass) {
    if (conformityService == null || systemId == null) {
      return List.of();
    }
    return conformityService.reviewBlockers(systemId, riskClass);
  }

  private boolean humanOversightPassed(UUID systemId) {
    if (systemControls == null || controls == null || tenantContext == null || systemId == null) {
      return false;
    }
    return controlStatusIndex(systemId).get("HUMAN_OVERSIGHT") == ControlStatus.PASS;
  }

  private boolean hasOversightGap(List<String> gaps) {
    return gaps.stream().anyMatch(gap -> gap.toLowerCase().contains("oversight"));
  }
}
