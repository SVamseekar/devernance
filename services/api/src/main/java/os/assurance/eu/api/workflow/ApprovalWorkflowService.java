package os.assurance.eu.api.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseInputsChanged;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApprovalWorkflowService {

  private final ApprovalWorkflowRepository repository;
  private final WorkflowNotificationRepository notifications;
  private final UserJpaRepository users;
  private final AuditService auditService;
  private final TenantContext tenantContext;
  private final ApplicationEventPublisher events;

  public ApprovalWorkflowService(
      ApprovalWorkflowRepository repository,
      WorkflowNotificationRepository notifications,
      UserJpaRepository users,
      AuditService auditService,
      TenantContext tenantContext,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.notifications = notifications;
    this.users = users;
    this.auditService = auditService;
    this.tenantContext = tenantContext;
    this.events = events;
  }

  /**
   * Opens a new approval workflow cycle for the given AI system.
   *
   * <p>Skips if:
   * <ul>
   *   <li>System is PROHIBITED</li>
   *   <li>Non-HIGH risk system with PASS decision (unless HIGH_RISK_PASS trigger)</li>
   *   <li>HIGH_RISK_PASS trigger and prior workflows already exist</li>
   * </ul>
   */
  @Transactional
  public void openCycle(AiSystem system, WorkflowTrigger trigger) {
    // Never open workflow for PROHIBITED systems
    if (system.riskClass() == RiskClass.PROHIBITED) {
      return;
    }

    // For non-HIGH risk systems, only open if decision is not PASS
    if (system.riskClass() != RiskClass.HIGH && system.releaseDecision() == ReleaseDecision.PASS) {
      return;
    }

    // For HIGH_RISK_PASS trigger, skip if prior workflows already exist
    if (trigger == WorkflowTrigger.HIGH_RISK_PASS) {
      List<ApprovalWorkflow> prior = repository.findAllBySystemId(system.id());
      if (!prior.isEmpty()) {
        return;
      }
    }

    // Supersede any existing OPEN workflow
    Optional<ApprovalWorkflow> existing = repository.findOpenBySystemId(system.id());
    existing.ifPresent(w -> {
      ApprovalWorkflow superseded = new ApprovalWorkflow(
          w.id(), w.systemId(), w.trigger(), WorkflowStatus.SUPERSEDED,
          w.stages(), w.openedAt(), Instant.now(), w.createdAt());
      repository.saveWorkflow(superseded);
    });

    // Determine stage statuses based on risk class and release decision
    List<StageStatus> stageStatuses = resolveStageStatuses(system);

    // Create the new workflow
    Instant now = Instant.now();
    ApprovalWorkflow newWorkflow = new ApprovalWorkflow(
        UUID.randomUUID(), system.id(), trigger, WorkflowStatus.OPEN,
        List.of(), now, null, now);
    ApprovalWorkflow saved = repository.saveWorkflow(newWorkflow);

    // Create stages — for HIGH risk, persist all 3 (including SKIPPED for auditability);
    // for non-HIGH, only persist active (PENDING) stages.
    boolean isHighRisk = system.riskClass() == RiskClass.HIGH;
    StageDefinition[] defs = stageDefinitions();
    List<ApprovalStage> createdStages = new ArrayList<>();
    for (int i = 0; i < defs.length; i++) {
      StageStatus stageStatus = stageStatuses.get(i);
      if (!isHighRisk && stageStatus == StageStatus.SKIPPED) {
        continue; // non-HIGH: don't persist SKIPPED stages
      }
      StageDefinition def = defs[i];
      UUID assignedReviewerId = findReviewer(def.requiredRole()).map(UserEntity::id).orElse(null);
      ApprovalStage stage = new ApprovalStage(
          UUID.randomUUID(), saved.id(), def.order(), def.type(),
          def.requiredRole(), assignedReviewerId, stageStatus, null, null, null, null, null, now);
      ApprovalStage savedStage = repository.saveStage(stage);
      createdStages.add(savedStage);
    }
    notifyNextPendingReviewer(saved, createdStages);
    events.publishEvent(new ReleaseInputsChanged(system.id()));
  }

  /**
   * Approve a specific stage in a workflow.
   */
  @Transactional
  public ApprovalWorkflow approveStage(
      UUID workflowId, UUID stageId, String rationale, String oversightEvidence) {
    UserEntity actor = resolveActor();
    ApprovalWorkflow workflow = resolveWorkflow(workflowId);
    ApprovalStage stage = resolveStage(workflow, stageId);

    // Check stage is not already completed
    if (isDone(stage.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "409: Stage is already in terminal status: " + stage.status());
    }

    // Check role
    checkRole(actor, stage);
    checkTurn(workflow, stage);
    checkOversightEvidence(stage, oversightEvidence);

    // Mark stage approved
    ApprovalStage updated = new ApprovalStage(
        stage.id(), stage.workflowId(), stage.stageOrder(), stage.stageType(),
        stage.requiredRole(), stage.assignedReviewerId(), StageStatus.APPROVED,
        actor.id(), rationale, normalize(oversightEvidence), Instant.now(),
        stage.notificationSentAt(), stage.createdAt());
    repository.saveStage(updated);

    // Rebuild stages list with the update
    List<ApprovalStage> updatedStages = replaceStage(workflow.stages(), updated);

    // Audit
    auditService.append(workflow.systemId(), "WORKFLOW_STAGE_APPROVED",
        "ApprovalStage", stageId.toString(),
        Map.of("workflowId", workflowId.toString(), "rationale", rationale));

    // Check if all stages are done
    if (allDone(updatedStages)) {
      return closeWorkflow(workflow, WorkflowStatus.APPROVED, updatedStages);
    }

    notifyNextPendingReviewer(workflow, updatedStages);
    events.publishEvent(new ReleaseInputsChanged(workflow.systemId()));
    return new ApprovalWorkflow(workflow.id(), workflow.systemId(), workflow.trigger(),
        WorkflowStatus.OPEN, updatedStages, workflow.openedAt(), null, workflow.createdAt());
  }

  /**
   * Reject a specific stage in a workflow.
   */
  @Transactional
  public ApprovalWorkflow rejectStage(UUID workflowId, UUID stageId, String rationale) {
    if (rationale == null || rationale.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rationale is required for rejection");
    }
    UserEntity actor = resolveActor();
    ApprovalWorkflow workflow = resolveWorkflow(workflowId);
    ApprovalStage stage = resolveStage(workflow, stageId);

    // Check stage is not already completed
    if (isDone(stage.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "409: Stage is already in terminal status: " + stage.status());
    }

    // Check role
    checkRole(actor, stage);
    checkTurn(workflow, stage);

    // Mark stage rejected
    ApprovalStage updated = new ApprovalStage(
        stage.id(), stage.workflowId(), stage.stageOrder(), stage.stageType(),
        stage.requiredRole(), stage.assignedReviewerId(), StageStatus.REJECTED,
        actor.id(), rationale, stage.oversightEvidence(), Instant.now(),
        stage.notificationSentAt(), stage.createdAt());
    repository.saveStage(updated);

    // Audit
    auditService.append(workflow.systemId(), "WORKFLOW_STAGE_REJECTED",
        "ApprovalStage", stageId.toString(),
        Map.of("workflowId", workflowId.toString(), "rationale", rationale));

    List<ApprovalStage> updatedStages = replaceStage(workflow.stages(), updated);
    return closeWorkflow(workflow, WorkflowStatus.REJECTED, updatedStages);
  }

  /**
   * Override a specific stage (ADMIN only).
   */
  @Transactional
  public ApprovalWorkflow overrideStage(UUID workflowId, UUID stageId, String rationale) {
    if (rationale == null || rationale.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rationale is required for override");
    }
    UserEntity actor = resolveActor();

    // Only ADMIN can override
    if (actor.role() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "403: Only ADMIN role can override stages");
    }

    ApprovalWorkflow workflow = resolveWorkflow(workflowId);
    ApprovalStage stage = resolveStage(workflow, stageId);

    // Mark stage overridden
    ApprovalStage updated = new ApprovalStage(
        stage.id(), stage.workflowId(), stage.stageOrder(), stage.stageType(),
        stage.requiredRole(), stage.assignedReviewerId(), StageStatus.OVERRIDDEN,
        actor.id(), rationale, stage.oversightEvidence(), Instant.now(),
        stage.notificationSentAt(), stage.createdAt());
    repository.saveStage(updated);

    // Audit
    auditService.append(workflow.systemId(), "WORKFLOW_STAGE_OVERRIDDEN",
        "ApprovalStage", stageId.toString(),
        Map.of("workflowId", workflowId.toString(), "rationale", rationale));

    List<ApprovalStage> updatedStages = replaceStage(workflow.stages(), updated);

    if (allDone(updatedStages)) {
      return closeWorkflow(workflow, WorkflowStatus.APPROVED, updatedStages);
    }

    notifyNextPendingReviewer(workflow, updatedStages);
    ApprovalWorkflow reopened = new ApprovalWorkflow(workflow.id(), workflow.systemId(), workflow.trigger(),
        WorkflowStatus.OPEN, updatedStages, workflow.openedAt(), null, workflow.createdAt());
    if (workflow.status() != WorkflowStatus.OPEN) {
      // Overriding the stage that rejected a cycle reopens it for the stages after it.
      repository.saveWorkflow(reopened);
    }
    events.publishEvent(new ReleaseInputsChanged(workflow.systemId()));
    return reopened;
  }

  @Transactional(readOnly = true)
  public List<ApprovalWorkflow> listBySystemId(UUID systemId) {
    return repository.findAllBySystemId(systemId);
  }

  @Transactional(readOnly = true)
  public List<ApprovalWorkflow> listOpen() {
    return repository.findOpen();
  }

  @Transactional(readOnly = true)
  public List<ApprovalWorkflow> listMine() {
    UserEntity actor = resolveActor();
    return repository.findOpen().stream()
        .filter(workflow -> nextPendingStage(workflow.stages())
            .filter(stage -> canAct(actor, stage))
            .isPresent())
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WorkflowNotification> listMyNotifications() {
    return notifications.findMine();
  }

  @Transactional(readOnly = true)
  public Optional<ApprovalWorkflow> findActive(UUID systemId) {
    return repository.findOpenBySystemId(systemId);
  }

  // ---- private helpers ----

  private UserEntity resolveActor() {
    UUID actorId = tenantContext.actorId();
    UUID tenantId = tenantContext.tenantId();
    return users.findByIdAndTenantId(actorId, tenantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "404: Actor not found: " + actorId));
  }

  /** The system a workflow really belongs to, for callers that must not trust a systemId in the URL. */
  @Transactional(readOnly = true)
  public UUID systemIdOf(UUID workflowId) {
    return resolveWorkflow(workflowId).systemId();
  }

  private ApprovalWorkflow resolveWorkflow(UUID workflowId) {
    return repository.findById(workflowId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "404: Workflow not found: " + workflowId));
  }

  private ApprovalStage resolveStage(ApprovalWorkflow workflow, UUID stageId) {
    return workflow.stages().stream()
        .filter(s -> s.id().equals(stageId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "404: Stage not found: " + stageId));
  }

  private void checkRole(UserEntity actor, ApprovalStage stage) {
    if (canAct(actor, stage)) {
      return;
    }
    if (stage.assignedReviewerId() != null && !stage.assignedReviewerId().equals(actor.id())
        && actor.role() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "403: Stage is assigned to another reviewer");
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "403: Actor role " + actor.role() + " cannot act on stage requiring " + stage.requiredRole());
  }

  private boolean canAct(UserEntity actor, ApprovalStage stage) {
    if (actor.role() == UserRole.ADMIN) {
      return true;
    }
    if (!actor.role().name().equals(stage.requiredRole())) {
      return false;
    }
    return stage.assignedReviewerId() == null || stage.assignedReviewerId().equals(actor.id());
  }

  private void checkTurn(ApprovalWorkflow workflow, ApprovalStage stage) {
    Optional<ApprovalStage> next = nextPendingStage(workflow.stages());
    if (next.isPresent() && !next.get().id().equals(stage.id())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "409: Earlier approval stages must be completed first");
    }
  }

  private void checkOversightEvidence(ApprovalStage stage, String oversightEvidence) {
    if (stage.stageType() == StageType.LEGAL_SIGNOFF
        && (oversightEvidence == null || oversightEvidence.isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Human oversight evidence is required for legal signoff");
    }
  }

  private Optional<UserEntity> findReviewer(String requiredRole) {
    Optional<UserEntity> reviewer = users.findFirstByTenantIdAndRoleOrderByCreatedAtAsc(
        tenantContext.tenantId(), UserRole.valueOf(requiredRole));
    return reviewer == null ? Optional.empty() : reviewer;
  }

  private Optional<ApprovalStage> nextPendingStage(List<ApprovalStage> stages) {
    return stages.stream()
        .filter(s -> s.status() == StageStatus.PENDING)
        .min((left, right) -> Integer.compare(left.stageOrder(), right.stageOrder()));
  }

  private void notifyNextPendingReviewer(ApprovalWorkflow workflow, List<ApprovalStage> stages) {
    nextPendingStage(stages)
        .filter(stage -> stage.notificationSentAt() == null)
        .ifPresent(stage -> notifyStageReviewer(workflow, stage, "WORKFLOW_STAGE_ASSIGNED"));
  }

  private void notifyStageReviewer(
      ApprovalWorkflow workflow, ApprovalStage stage, String eventType) {
    if (stage.assignedReviewerId() == null) {
      return;
    }
    Instant now = Instant.now();
    notifications.save(new WorkflowNotification(
        UUID.randomUUID(),
        workflow.id(),
        stage.id(),
        stage.assignedReviewerId(),
        eventType,
        "Approval required for " + stage.stageType().name().replace('_', ' '),
        null,
        now));
    repository.saveStage(new ApprovalStage(
        stage.id(), stage.workflowId(), stage.stageOrder(), stage.stageType(),
        stage.requiredRole(), stage.assignedReviewerId(), stage.status(), stage.actorId(),
        stage.rationale(), stage.oversightEvidence(), stage.actedAt(), now, stage.createdAt()));
  }

  private boolean isDone(StageStatus status) {
    return status == StageStatus.APPROVED
        || status == StageStatus.OVERRIDDEN
        || status == StageStatus.SKIPPED
        || status == StageStatus.REJECTED;
  }

  private boolean allDone(List<ApprovalStage> stages) {
    return stages.stream().allMatch(s -> isDone(s.status()));
  }

  private List<ApprovalStage> replaceStage(List<ApprovalStage> stages, ApprovalStage updated) {
    List<ApprovalStage> result = new ArrayList<>();
    for (ApprovalStage s : stages) {
      result.add(s.id().equals(updated.id()) ? updated : s);
    }
    return result;
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private ApprovalWorkflow closeWorkflow(
      ApprovalWorkflow workflow, WorkflowStatus finalStatus, List<ApprovalStage> stages) {
    ApprovalWorkflow closed = new ApprovalWorkflow(
        workflow.id(), workflow.systemId(), workflow.trigger(), finalStatus,
        stages, workflow.openedAt(), Instant.now(), workflow.createdAt());
    repository.saveWorkflow(closed);

    auditService.append(workflow.systemId(), "WORKFLOW_CLOSED",
        "ApprovalWorkflow", workflow.id().toString(),
        Map.of("finalStatus", finalStatus.name()));
    events.publishEvent(new ReleaseInputsChanged(workflow.systemId()));

    return closed;
  }

  /**
   * Determine per-stage statuses based on risk class and release decision.
   *
   * <p>Returns a list of 3 statuses (one per stage: ENG_LEAD, COMPLIANCE, LEGAL).
   *
   * <ul>
   *   <li>HIGH + PASS: stage 1 SKIPPED, stages 2+3 PENDING</li>
   *   <li>HIGH (not PASS): all 3 PENDING</li>
   *   <li>non-HIGH BLOCKED: all 3 PENDING</li>
   *   <li>non-HIGH REVIEW: stages 1+2 PENDING, stage 3 SKIPPED</li>
   * </ul>
   */
  private List<StageStatus> resolveStageStatuses(AiSystem system) {
    if (system.riskClass() == RiskClass.HIGH) {
      if (system.releaseDecision() == ReleaseDecision.PASS) {
        // Stage 1 skipped — compliance + legal review still required
        return List.of(StageStatus.SKIPPED, StageStatus.PENDING, StageStatus.PENDING);
      } else {
        // All 3 stages required
        return List.of(StageStatus.PENDING, StageStatus.PENDING, StageStatus.PENDING);
      }
    } else {
      // non-HIGH
      if (system.releaseDecision() == ReleaseDecision.BLOCKED) {
        return List.of(StageStatus.PENDING, StageStatus.PENDING, StageStatus.PENDING);
      } else {
        // REVIEW — stages 1+2 pending, legal skipped
        return List.of(StageStatus.PENDING, StageStatus.PENDING, StageStatus.SKIPPED);
      }
    }
  }

  private record StageDefinition(int order, StageType type, String requiredRole) {}

  private StageDefinition[] stageDefinitions() {
    return new StageDefinition[]{
        new StageDefinition(1, StageType.ENG_LEAD_REVIEW, "AI_ENGINEERING_LEAD"),
        new StageDefinition(2, StageType.COMPLIANCE_REVIEW, "COMPLIANCE_OFFICER"),
        new StageDefinition(3, StageType.LEGAL_SIGNOFF, "LEGAL_COUNSEL")
    };
  }
}
