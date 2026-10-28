package os.assurance.eu.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.DataContractStatus;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApprovalWorkflowServiceTest {
  private ApprovalWorkflowRepository repository;
  private WorkflowNotificationRepository notifications;
  private UserJpaRepository users;
  private AuditService auditService;
  private TenantContext tenantContext;
  private ApprovalWorkflowService service;

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SYSTEM_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(ApprovalWorkflowRepository.class);
    notifications = mock(WorkflowNotificationRepository.class);
    users = mock(UserJpaRepository.class);
    auditService = mock(AuditService.class);
    tenantContext = mock(TenantContext.class);
    when(tenantContext.tenantId()).thenReturn(TENANT_ID);
    when(tenantContext.actorId()).thenReturn(ACTOR_ID);
    service = new ApprovalWorkflowService(repository, notifications, users, auditService, tenantContext,
        org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
  }

  @Test
  void opensThreeStageWorkflowForHighRiskBlocked() {
    AiSystem system = system(RiskClass.HIGH, ReleaseDecision.BLOCKED);
    when(repository.findOpenBySystemId(SYSTEM_ID)).thenReturn(Optional.empty());
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));

    service.openCycle(system, WorkflowTrigger.SYSTEM_CREATED);

    verify(repository).saveWorkflow(any());
    verify(repository, org.mockito.Mockito.times(3)).saveStage(any());
  }

  @Test
  void opensTwoStageWorkflowForLimitedReview() {
    AiSystem system = system(RiskClass.LIMITED, ReleaseDecision.REVIEW);
    when(repository.findOpenBySystemId(SYSTEM_ID)).thenReturn(Optional.empty());
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));

    service.openCycle(system, WorkflowTrigger.SYSTEM_CREATED);

    verify(repository, org.mockito.Mockito.times(2)).saveStage(any());
  }

  @Test
  void supersedesPriorOpenWorkflowBeforeOpeningNew() {
    ApprovalWorkflow existing = workflow(WorkflowStatus.OPEN, List.of());
    when(repository.findOpenBySystemId(SYSTEM_ID)).thenReturn(Optional.of(existing));
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));

    AiSystem system = system(RiskClass.HIGH, ReleaseDecision.REVIEW);
    service.openCycle(system, WorkflowTrigger.EVAL_REGRESSION);

    verify(repository, org.mockito.Mockito.atLeast(2)).saveWorkflow(any());
  }

  @Test
  void approveStageAdvancesWorkflow() {
    ApprovalStage pendingStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.PENDING);
    ApprovalStage nextStage = stage(UUID.randomUUID(), 2, StageType.COMPLIANCE_REVIEW,
        "COMPLIANCE_OFFICER", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN,
        List.of(pendingStage, nextStage));

    UserEntity actor = actor(UserRole.AI_ENGINEERING_LEAD);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));

    ApprovalWorkflow result = service.approveStage(
        openWorkflow.id(), pendingStage.id(), "Looks good", null);

    assertThat(result.status()).isEqualTo(WorkflowStatus.OPEN);
  }

  @Test
  void approveLastStageClosesWorkflowAsApproved() {
    ApprovalStage onlyStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN, List.of(onlyStage));

    UserEntity actor = actor(UserRole.AI_ENGINEERING_LEAD);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));

    ApprovalWorkflow result = service.approveStage(
        openWorkflow.id(), onlyStage.id(), "Approved", null);

    assertThat(result.status()).isEqualTo(WorkflowStatus.APPROVED);
  }

  @Test
  void rejectStageClosesWorkflowAsRejected() {
    ApprovalStage pendingStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN, List.of(pendingStage));

    UserEntity actor = actor(UserRole.AI_ENGINEERING_LEAD);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));

    ApprovalWorkflow result = service.rejectStage(
        openWorkflow.id(), pendingStage.id(), "Missing bias eval");

    assertThat(result.status()).isEqualTo(WorkflowStatus.REJECTED);
  }

  @Test
  void rejectsApproveFromWrongRole() {
    ApprovalStage pendingStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN, List.of(pendingStage));

    UserEntity actor = actor(UserRole.AUDITOR);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));

    assertThatThrownBy(() ->
        service.approveStage(openWorkflow.id(), pendingStage.id(), "ok", null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403");
  }

  @Test
  void rejectsActingOnAlreadyApprovedStage() {
    ApprovalStage doneStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.APPROVED);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN, List.of(doneStage));

    UserEntity actor = actor(UserRole.AI_ENGINEERING_LEAD);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));

    assertThatThrownBy(() ->
        service.approveStage(openWorkflow.id(), doneStage.id(), "again", null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void adminCanOverrideAnyStage() {
    ApprovalStage pendingStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN, List.of(pendingStage));

    UserEntity actor = actor(UserRole.ADMIN);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));

    ApprovalWorkflow result = service.overrideStage(
        openWorkflow.id(), pendingStage.id(), "Urgent release");

    assertThat(result).isNotNull();
  }

  @Test
  void rejectsOutOfOrderStageApproval() {
    ApprovalStage firstStage = stage(UUID.randomUUID(), 1, StageType.ENG_LEAD_REVIEW,
        "AI_ENGINEERING_LEAD", StageStatus.PENDING);
    ApprovalStage secondStage = stage(UUID.randomUUID(), 2, StageType.COMPLIANCE_REVIEW,
        "COMPLIANCE_OFFICER", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN,
        List.of(firstStage, secondStage));

    UserEntity actor = actor(UserRole.COMPLIANCE_OFFICER);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));

    assertThatThrownBy(() ->
        service.approveStage(openWorkflow.id(), secondStage.id(), "ok", null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void legalSignoffRequiresOversightEvidence() {
    ApprovalStage legalStage = stage(UUID.randomUUID(), 3, StageType.LEGAL_SIGNOFF,
        "LEGAL_COUNSEL", StageStatus.PENDING);
    ApprovalWorkflow openWorkflow = workflow(WorkflowStatus.OPEN, List.of(legalStage));

    UserEntity actor = actor(UserRole.LEGAL_COUNSEL);
    when(users.findByIdAndTenantId(ACTOR_ID, TENANT_ID)).thenReturn(Optional.of(actor));
    when(repository.findById(openWorkflow.id())).thenReturn(Optional.of(openWorkflow));

    assertThatThrownBy(() ->
        service.approveStage(openWorkflow.id(), legalStage.id(), "ok", null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Human oversight evidence");
  }

  @Test
  void doesNotOpenWorkflowForProhibitedSystem() {
    AiSystem system = system(RiskClass.PROHIBITED, ReleaseDecision.BLOCKED);
    service.openCycle(system, WorkflowTrigger.SYSTEM_CREATED);
    verify(repository, org.mockito.Mockito.never()).saveWorkflow(any());
  }

  @Test
  void doesNotOpenWorkflowForLimitedPassSystem() {
    AiSystem system = system(RiskClass.LIMITED, ReleaseDecision.PASS);
    service.openCycle(system, WorkflowTrigger.SYSTEM_CREATED);
    verify(repository, org.mockito.Mockito.never()).saveWorkflow(any());
  }

  @Test
  void highRiskPassOpensComplianceAndLegalOnlyWhenNoPriorWorkflow() {
    AiSystem system = system(RiskClass.HIGH, ReleaseDecision.PASS);
    when(repository.findOpenBySystemId(SYSTEM_ID)).thenReturn(Optional.empty());
    when(repository.findAllBySystemId(SYSTEM_ID)).thenReturn(List.of());
    when(repository.saveWorkflow(any())).thenAnswer(i -> i.getArgument(0));
    when(repository.saveStage(any())).thenAnswer(i -> i.getArgument(0));

    service.openCycle(system, WorkflowTrigger.HIGH_RISK_PASS);

    verify(repository, org.mockito.Mockito.times(3)).saveStage(any());
  }

  // ---- helpers ----

  private AiSystem system(RiskClass riskClass, ReleaseDecision decision) {
    Instant now = Instant.now();
    return new AiSystem(SYSTEM_ID, "Test", "Owner", "Purpose", riskClass,
        "Basis", "EU", 80, 80, DataContractStatus.HEALTHY, decision,
        List.of(), null, null, null, List.of(), null, null, List.of(), now, now);
  }

  private ApprovalWorkflow workflow(WorkflowStatus status, List<ApprovalStage> stages) {
    Instant now = Instant.now();
    return new ApprovalWorkflow(UUID.randomUUID(), SYSTEM_ID,
        WorkflowTrigger.SYSTEM_CREATED, status, stages, now, null, now);
  }

  private ApprovalStage stage(UUID id, int order, StageType type,
      String requiredRole, StageStatus status) {
    return new ApprovalStage(id, UUID.randomUUID(), order, type,
        requiredRole, null, status, null, null, null, null, null, Instant.now());
  }

  private UserEntity actor(UserRole role) {
    return new UserEntity(ACTOR_ID, TENANT_ID, "actor@example.com", role, Instant.now());
  }
}
