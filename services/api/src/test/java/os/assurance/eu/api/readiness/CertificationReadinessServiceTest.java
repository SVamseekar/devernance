package os.assurance.eu.api.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditChainVerifyResponse;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.control.ControlStatus;
import os.assurance.eu.api.control.SystemControl;
import os.assurance.eu.api.determination.DeterminationRunJpaRepository;
import os.assurance.eu.api.evidence.EvidenceDocument;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.DataContractStatus;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateResponse;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CertificationReadinessServiceTest {
  private AiSystemRepository systems;
  private ReleaseGateService releaseGateService;
  private ControlService controlService;
  private EvidenceRepository evidenceRepository;
  private ApprovalWorkflowService approvalWorkflowService;
  private DeterminationRunJpaRepository determinationRuns;
  private AuditService auditService;
  private TenantContext tenantContext;
  private CertificationReadinessService service;

  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SYSTEM = UUID.fromString("00000000-0000-4000-8000-000000000099");

  @BeforeEach
  void setUp() {
    systems = mock(AiSystemRepository.class);
    releaseGateService = mock(ReleaseGateService.class);
    controlService = mock(ControlService.class);
    evidenceRepository = mock(EvidenceRepository.class);
    approvalWorkflowService = mock(ApprovalWorkflowService.class);
    determinationRuns = mock(DeterminationRunJpaRepository.class);
    auditService = mock(AuditService.class);
    tenantContext = mock(TenantContext.class);
    when(tenantContext.tenantId()).thenReturn(TENANT);

    CertificationReadinessProperties props = new CertificationReadinessProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC);
    service = new CertificationReadinessService(
        systems,
        releaseGateService,
        controlService,
        evidenceRepository,
        approvalWorkflowService,
        determinationRuns,
        auditService,
        tenantContext,
        props,
        clock);
  }

  @Test
  void blockedReleaseIsNotReady() {
    AiSystem system = system(RiskClass.HIGH, 40, 50, DataContractStatus.BREACH,
        List.of("Human oversight SOP missing"), ReleaseDecision.BLOCKED);
    when(releaseGateService.calculate(system))
        .thenReturn(new ReleaseGateResponse(SYSTEM, ReleaseDecision.BLOCKED,
            List.of("Data contract breach is open")));
    when(controlService.listForSystem(SYSTEM)).thenReturn(List.of());
    when(evidenceRepository.findDocumentsBySystemId(SYSTEM)).thenReturn(List.of());
    when(approvalWorkflowService.listBySystemId(SYSTEM)).thenReturn(List.of());
    when(determinationRuns.findFirstByTenantIdAndSystemIdOrderByCreatedAtDesc(TENANT, SYSTEM))
        .thenReturn(Optional.empty());
    when(auditService.recentVerification()).thenReturn(new AuditChainVerifyResponse(true, 3, null));

    CertificationReadinessResponse report = service.assess(system);

    assertThat(report.readinessStatus()).isEqualTo(CertificationReadinessStatus.NOT_READY);
    assertThat(report.gaps()).isNotEmpty();
    assertThat(report.dimensions()).hasSize(9);
    assertThat(report.productLabel()).contains("Certification readiness");
    assertThat(report.disclaimer().toLowerCase()).doesNotContain("you are certified");
  }

  @Test
  void fullHappyPathIsReadyForReview() {
    AiSystem system = system(RiskClass.LIMITED, 95, 92, DataContractStatus.HEALTHY,
        List.of(), ReleaseDecision.PASS);
    when(releaseGateService.calculate(system))
        .thenReturn(new ReleaseGateResponse(SYSTEM, ReleaseDecision.PASS, List.of()));
    when(controlService.listForSystem(SYSTEM)).thenReturn(List.of(
        control("RISK_MANAGEMENT", ControlStatus.PASS),
        control("DATA_GOVERNANCE", ControlStatus.PASS),
        control("TRANSPARENCY", ControlStatus.PASS)));
    when(evidenceRepository.findDocumentsBySystemId(SYSTEM)).thenReturn(List.of(
        new EvidenceDocument(
            UUID.randomUUID(), SYSTEM, "POLICY", "Transparency SOP",
            "memory://sop", "abc", 1, "indexed", Instant.parse("2026-07-20T11:00:00Z"))));
    when(approvalWorkflowService.listBySystemId(SYSTEM)).thenReturn(List.of());
    when(determinationRuns.findFirstByTenantIdAndSystemIdOrderByCreatedAtDesc(TENANT, SYSTEM))
        .thenReturn(Optional.of(mock(os.assurance.eu.api.determination.DeterminationRunEntity.class)));
    when(auditService.recentVerification()).thenReturn(new AuditChainVerifyResponse(true, 12, null));

    CertificationReadinessResponse report = service.assess(system);

    assertThat(report.readinessStatus()).isEqualTo(CertificationReadinessStatus.READY_FOR_REVIEW);
    assertThat(report.score()).isGreaterThanOrEqualTo(90);
    assertThat(report.gaps().stream().noneMatch(g -> g.severity() == ReadinessGapSeverity.CRITICAL
        || g.severity() == ReadinessGapSeverity.HIGH)).isTrue();
  }

  private AiSystem system(
      RiskClass risk,
      int evidence,
      int eval,
      DataContractStatus contract,
      List<String> gaps,
      ReleaseDecision decision) {
    Instant now = Instant.parse("2026-07-20T10:00:00Z");
    return new AiSystem(
        SYSTEM,
        "Test System",
        "Owner",
        "Purpose",
        risk,
        "Test risk basis",
        "EU",
        evidence,
        eval,
        contract,
        decision,
        gaps,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        List.of(),
        now,
        now);
  }

  private SystemControl control(String code, ControlStatus status) {
    return new SystemControl(
        UUID.randomUUID(),
        SYSTEM,
        UUID.randomUUID(),
        code,
        code,
        "GOVERNANCE",
        status,
        true,
        null,
        null,
        Instant.parse("2026-07-20T11:00:00Z"));
  }
}
