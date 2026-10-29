package os.assurance.eu.api.control;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.sector.SectorControlDef;
import os.assurance.eu.api.sector.SectorPack;
import os.assurance.eu.api.sector.SectorPackRegistry;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlService {
  private final ControlJpaRepository controls;
  private final SystemControlJpaRepository systemControls;
  private final AiSystemRepository systems;
  private final ReleaseGateService releaseGateService;
  private final AuditService auditService;
  private final TenantContext tenantContext;
  private final SectorPackRegistry sectorPackRegistry;

  public ControlService(
      ControlJpaRepository controls,
      SystemControlJpaRepository systemControls,
      AiSystemRepository systems,
      ReleaseGateService releaseGateService,
      AuditService auditService,
      TenantContext tenantContext,
      SectorPackRegistry sectorPackRegistry) {
    this.controls = controls;
    this.systemControls = systemControls;
    this.systems = systems;
    this.releaseGateService = releaseGateService;
    this.auditService = auditService;
    this.tenantContext = tenantContext;
    this.sectorPackRegistry = sectorPackRegistry;
  }

  @Transactional(readOnly = true)
  public List<Control> listCatalog() {
    ensureCatalogSeeded();
    return controls.findAll().stream().map(ControlEntity::toDomain).toList();
  }

  @Transactional(readOnly = true)
  public List<SystemControl> listForSystem(UUID systemId) {
    requireSystem(systemId);
    ensureCatalogSeeded();
    Map<UUID, Control> catalog = controls.findAll().stream()
        .map(ControlEntity::toDomain)
        .collect(Collectors.toMap(Control::id, c -> c));
    return systemControls
        .findAllByTenantIdAndSystemIdOrderByUpdatedAtDesc(tenantContext.tenantId(), systemId)
        .stream()
        .map(entity -> toView(entity, catalog.get(entity.controlId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<String> blockerLabels(UUID systemId) {
    return listForSystem(systemId).stream()
        .filter(sc -> sc.status() == ControlStatus.BLOCKED)
        .map(sc -> "CONTROL:" + sc.controlCode())
        .toList();
  }

  @Transactional
  public void attachApplicableControls(AiSystem system) {
    ensureCatalogSeeded();
    Instant now = Instant.now();
    for (Control control : listCatalog()) {
      if (!appliesTo(control, system.riskClass())) {
        continue;
      }
      // Baseline catalog controls only here; sector overlays attached below.
      if (isSectorOverlayCode(control.code())) {
        continue;
      }
      attachIfMissing(system, control, now, null);
    }
    attachSectorPackControls(system, now);
  }

  /**
   * Attach enabled sector-pack control overlays when {@code system.sector} matches a pack.
   */
  private void attachSectorPackControls(AiSystem system, Instant now) {
    if (sectorPackRegistry == null) {
      return;
    }
    sectorPackRegistry.resolveForSector(system.sector()).ifPresent(pack -> {
      for (SectorControlDef def : pack.extraControls()) {
        Control control = ensureControlFromDef(def);
        if (!appliesTo(control, system.riskClass())) {
          continue;
        }
        attachIfMissing(
            system,
            control,
            now,
            "Attached by sector pack '" + pack.id() + "' (" + pack.displayName() + ")");
      }
    });
  }

  private void attachIfMissing(AiSystem system, Control control, Instant now, String notes) {
    boolean exists = systemControls
        .findByTenantIdAndSystemIdAndControlId(
            tenantContext.tenantId(), system.id(), control.id())
        .isPresent();
    if (exists) {
      return;
    }
    ControlStatus initial = initialStatus(control, system);
    systemControls.save(new SystemControlEntity(
        tenantContext.tenantId(),
        UUID.randomUUID(),
        system.id(),
        control.id(),
        initial,
        true,
        null,
        notes,
        now));
  }

  private boolean isSectorOverlayCode(String code) {
    if (code == null || sectorPackRegistry == null) {
      return false;
    }
    for (SectorPack pack : sectorPackRegistry.enabledPacks()) {
      for (SectorControlDef def : pack.extraControls()) {
        if (code.equalsIgnoreCase(def.code())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Ensure system_controls rows exist in REVIEW for the given catalog codes.
   * Does not downgrade PASS/BLOCKED statuses; only creates missing rows or
   * leaves existing rows unchanged.
   *
   * @return control codes newly opened in REVIEW
   */
  @Transactional
  public List<String> openControlsForReview(UUID systemId, List<String> controlCodes) {
    if (controlCodes == null || controlCodes.isEmpty()) {
      return List.of();
    }
    requireSystem(systemId);
    ensureCatalogSeeded();
    Instant now = Instant.now();
    List<String> opened = new ArrayList<>();
    for (String code : controlCodes) {
      if (code == null || code.isBlank()) {
        continue;
      }
      ControlEntity control = controls.findByCode(code.trim().toUpperCase(Locale.ROOT))
          .or(() -> controls.findByCode(code.trim()))
          .orElse(null);
      if (control == null) {
        continue;
      }
      boolean exists = systemControls
          .findByTenantIdAndSystemIdAndControlId(
              tenantContext.tenantId(), systemId, control.toDomain().id())
          .isPresent();
      if (exists) {
        continue;
      }
      systemControls.save(new SystemControlEntity(
          tenantContext.tenantId(),
          UUID.randomUUID(),
          systemId,
          control.toDomain().id(),
          ControlStatus.REVIEW,
          true,
          null,
          "Opened by assisted obligation determination — human review required",
          now));
      opened.add(control.toDomain().code());
    }
    if (!opened.isEmpty()) {
      recalculateSystemRelease(systemId);
    }
    return opened;
  }

  @Transactional
  public SystemControl updateSystemControl(
      UUID systemId, UUID controlId, UpdateSystemControlRequest request) {
    return updateSystemControl(systemId, controlId, request, UserRole.ADMIN);
  }

  /**
   * Separation of duties: the engineering lead who ships the system may raise a control to REVIEW or BLOCKED,
   * but only compliance (or an admin) can mark it PASS or lift a block, so nobody attests their own release.
   */
  public SystemControl updateSystemControl(
      UUID systemId, UUID controlId, UpdateSystemControlRequest request, UserRole actorRole) {
    requireSystem(systemId);
    SystemControlEntity existing = systemControls
        .findByTenantIdAndSystemIdAndControlId(tenantContext.tenantId(), systemId, controlId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System control not found"));
    if (actorRole == UserRole.AI_ENGINEERING_LEAD) {
      if (request.status() == ControlStatus.PASS) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Only compliance or an admin can mark a control PASS");
      }
      if (existing.status() == ControlStatus.BLOCKED && request.status() != ControlStatus.BLOCKED) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Only compliance or an admin can lift a BLOCKED control");
      }
    }
    SystemControlEntity updated = new SystemControlEntity(
        tenantContext.tenantId(),
        existing.id(),
        existing.systemId(),
        existing.controlId(),
        request.status(),
        existing.evidenceRequired(),
        tenantContext.actorId(),
        request.notes(),
        Instant.now());
    systemControls.save(updated);
    Control control = controls.findById(controlId)
        .map(ControlEntity::toDomain)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Control not found"));
    auditService.append(
        systemId,
        "system_control.updated",
        "system_control",
        updated.id().toString(),
        Map.of(
            "controlCode", control.code(),
            "status", request.status().name(),
            "notes", request.notes() == null ? "" : request.notes()));
    recalculateSystemRelease(systemId);
    return toView(updated, control);
  }

  private void recalculateSystemRelease(UUID systemId) {
    AiSystem system = requireSystem(systemId);
    List<String> controlBlockers = blockerLabels(systemId);
    ReleaseDecision decision = releaseGateService.calculate(system, controlBlockers).decision();
    systems.save(system.withReleaseDecision(decision));
  }

  private AiSystem requireSystem(UUID systemId) {
    return systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
  }

  private SystemControl toView(SystemControlEntity entity, Control control) {
    String code = control == null ? "UNKNOWN" : control.code();
    String name = control == null ? "Unknown control" : control.name();
    String category = control == null ? "UNKNOWN" : control.category();
    return new SystemControl(
        entity.id(),
        entity.systemId(),
        entity.controlId(),
        code,
        name,
        category,
        entity.status(),
        entity.evidenceRequired(),
        entity.reviewerId(),
        entity.notes(),
        entity.updatedAt());
  }

  private boolean appliesTo(Control control, RiskClass riskClass) {
    String applies = control.appliesToRiskClass();
    if (applies == null || applies.isBlank() || "ALL".equalsIgnoreCase(applies)) {
      return true;
    }
    Set<String> tokens = Arrays.stream(applies.split("[,|]"))
        .map(String::trim)
        .map(s -> s.toUpperCase(Locale.ROOT))
        .collect(Collectors.toSet());
    return tokens.contains(riskClass.name());
  }

  private ControlStatus initialStatus(Control control, AiSystem system) {
    if ("HUMAN_OVERSIGHT".equals(control.code())
        && system.riskClass() == RiskClass.HIGH
        && system.openGaps().stream().anyMatch(g -> g.toLowerCase(Locale.ROOT).contains("oversight"))) {
      return ControlStatus.BLOCKED;
    }
    if (system.riskClass() == RiskClass.HIGH || system.riskClass() == RiskClass.PROHIBITED) {
      return ControlStatus.REVIEW;
    }
    return ControlStatus.REVIEW;
  }

  @Transactional
  public void ensureCatalogSeeded() {
    if (controls.count() == 0) {
      List<Control> seed = baselineControls();
      for (Control control : seed) {
        controls.save(new ControlEntity(control));
      }
    }
    ensureSectorPackControlsSeeded();
  }

  /**
   * Seed sector-pack overlay controls into the global catalog (idempotent by code).
   */
  @Transactional
  public void ensureSectorPackControlsSeeded() {
    if (sectorPackRegistry == null) {
      return;
    }
    for (SectorPack pack : sectorPackRegistry.enabledPacks()) {
      for (SectorControlDef def : pack.extraControls()) {
        ensureControlFromDef(def);
      }
    }
  }

  private Control ensureControlFromDef(SectorControlDef def) {
    return controls.findByCode(def.code())
        .map(ControlEntity::toDomain)
        .orElseGet(() -> {
          Control created = new Control(
              UUID.randomUUID(),
              def.code(),
              def.name(),
              def.description(),
              def.appliesToRiskClass(),
              def.category());
          controls.save(new ControlEntity(created));
          return created;
        });
  }

  /** Codes of the baseline controls every catalog is seeded with. */
  public static List<String> baselineCodes() {
    return baselineControls().stream().map(Control::code).toList();
  }

  static List<Control> baselineControls() {
    List<Control> list = new ArrayList<>();
    list.add(control("RISK_MANAGEMENT", "Risk management system",
        "Documented risk management process across the AI lifecycle", "ALL", "GOVERNANCE"));
    list.add(control("DATA_GOVERNANCE", "Data and data governance",
        "Training/validation data quality, relevance, and bias mitigation", "ALL", "DATA"));
    list.add(control("RECORD_KEEPING", "Logging and record-keeping",
        "Automatic logs of system operation for post-market monitoring", "HIGH", "OPS"));
    list.add(control("TRANSPARENCY", "Transparency and provision of information",
        "Users informed they are interacting with an AI system where required", "ALL", "TRANSPARENCY"));
    list.add(control("HUMAN_OVERSIGHT", "Human oversight",
        "Human oversight measures proportionate to risk, including stop/override", "HIGH", "OVERSIGHT"));
    list.add(control("ACCURACY_ROBUSTNESS", "Accuracy, robustness, cybersecurity",
        "Appropriate levels of accuracy, robustness, and cyber resilience", "HIGH", "SECURITY"));
    list.add(control("CYBERSECURITY", "Cybersecurity controls",
        "Measures against data poisoning, model attacks, and unauthorized access", "HIGH", "SECURITY"));
    list.add(control("TECHNICAL_DOCUMENTATION", "Technical documentation",
        "Technical documentation sufficient for conformity assessment readiness", "HIGH", "DOCUMENTATION"));
    return list;
  }

  private static Control control(
      String code, String name, String description, String applies, String category) {
    return new Control(UUID.randomUUID(), code, name, description, applies, category);
  }
}
