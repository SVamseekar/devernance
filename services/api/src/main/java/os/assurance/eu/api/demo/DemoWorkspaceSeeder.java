package os.assurance.eu.api.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import os.assurance.eu.api.audit.AuditChainHeads;
import os.assurance.eu.api.contract.CreateDataContractRequest;
import os.assurance.eu.api.contract.DataContractService;
import os.assurance.eu.api.contract.DriftEventRequest;
import os.assurance.eu.api.contract.DriftSeverity;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.evidence.CreateEvidenceDocumentRequest;
import os.assurance.eu.api.evidence.EvidenceService;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.DataContractStatus;
import os.assurance.eu.api.system.GateRecalculator;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import os.assurance.eu.api.workflow.WorkflowTrigger;

/**
 * Seeds the shared, read-only demo workspace when {@code assurance.demo.enabled} is true. Idempotent. Goes
 * through services and repositories, never controllers, so role checks do not apply while seeding.
 */
@Component
@Order(3)
public class DemoWorkspaceSeeder implements ApplicationRunner {
  static final String HIGH_NAME = "Claims Triage AI (demo)";
  static final String LIMITED_NAME = "Support Copilot (demo)";

  private final boolean enabled;
  private final TenantJpaRepository tenants;
  private final UserJpaRepository users;
  private final AuditChainHeads chainHeads;
  private final TenantContext tenantContext;
  private final AiSystemRepository systems;
  private final ControlService controls;
  private final ReleaseGateService releaseGate;
  private final GateRecalculator gateRecalculator;
  private final EvidenceService evidence;
  private final DataContractService contracts;
  private final ApprovalWorkflowService workflows;

  public DemoWorkspaceSeeder(
      @Value("${assurance.demo.enabled:false}") boolean enabled,
      TenantJpaRepository tenants, UserJpaRepository users, AuditChainHeads chainHeads, TenantContext tenantContext,
      AiSystemRepository systems, ControlService controls, ReleaseGateService releaseGate,
      GateRecalculator gateRecalculator, EvidenceService evidence, DataContractService contracts,
      ApprovalWorkflowService workflows) {
    this.enabled = enabled;
    this.tenants = tenants;
    this.users = users;
    this.chainHeads = chainHeads;
    this.tenantContext = tenantContext;
    this.systems = systems;
    this.controls = controls;
    this.releaseGate = releaseGate;
    this.gateRecalculator = gateRecalculator;
    this.evidence = evidence;
    this.contracts = contracts;
    this.workflows = workflows;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    Instant now = Instant.now();
    tenants.findById(DemoProperties.DEMO_TENANT_ID).orElseGet(() ->
        tenants.save(new TenantEntity(DemoProperties.DEMO_TENANT_ID, "Devernance demo", "demo", "EU", now)));
    chainHeads.attachToNewTenant(DemoProperties.DEMO_TENANT_ID);
    users.findById(DemoProperties.DEMO_USER_ID).orElseGet(() -> {
      UserEntity viewer = new UserEntity(DemoProperties.DEMO_USER_ID, DemoProperties.DEMO_TENANT_ID,
          "demo@devernance.invalid", UserRole.AUDITOR, null, now);
      viewer.markEmailVerified(now);
      return users.save(viewer);
    });
    tenantContext.setOverrides(DemoProperties.DEMO_TENANT_ID, DemoProperties.DEMO_USER_ID);
    try {
      if (systems.findAll().stream().anyMatch(s -> HIGH_NAME.equals(s.name()))) {
        return;
      }
      AiSystem high = create(HIGH_NAME, "Claims Operations", "Prioritise and route insurance claims to reviewers",
          RiskClass.HIGH, "Annex III: access to essential private services (insurance)", "insurance", now);
      create(LIMITED_NAME, "Customer Success", "Answer customer questions with cited sources",
          RiskClass.LIMITED, "Article 50: interacts with natural persons", "customer-service", now);
      evidence.ingest(new CreateEvidenceDocumentRequest(high.id(), "POLICY", "Human oversight SOP",
          "memory://demo/oversight-sop",
          "Reviewers can override any automated routing decision. Claimants can appeal within 30 days. "
              + "Bias monitoring runs monthly and results are reviewed by the claims quality lead.",
          null, null));
      evidence.ingest(new CreateEvidenceDocumentRequest(high.id(), "MODEL_CARD", "Claims triage model card",
          "memory://demo/model-card",
          "Gradient-boosted classifier trained on 2023-2025 EU claims. Intended use: prioritisation only; "
              + "final decisions are made by human claims handlers. Known limits: sparse data for rare claim types.",
          null, null));
      var contract = contracts.createContract(new CreateDataContractRequest(
          high.id(), "claims-intake", "Data Platform", "v3", DataContractStatus.HEALTHY, 90));
      contracts.createDriftEvent(contract.id(), new DriftEventRequest(
          DriftSeverity.WARNING, "claim_amount", "Distribution shift in claim_amount above 2 standard deviations"));
      gateRecalculator.recalculate(high.id());
    } finally {
      tenantContext.clearOverrides();
    }
  }

  private AiSystem create(String name, String owner, String purpose, RiskClass risk, String basis, String sector,
      Instant now) {
    AiSystem draft = new AiSystem(UUID.randomUUID(), name, owner, purpose, risk, basis, "EU", 0, 0,
        DataContractStatus.HEALTHY, ReleaseDecision.REVIEW, List.of(), null, null, null, List.of(), sector, null,
        List.of(), now, now);
    AiSystem saved = systems.save(draft);
    controls.attachApplicableControls(saved);
    AiSystem decided = systems.save(withDecision(saved));
    workflows.openCycle(decided, WorkflowTrigger.SYSTEM_CREATED);
    return decided;
  }

  private AiSystem withDecision(AiSystem s) {
    return s.withReleaseDecision(releaseGate.calculate(s).decision());
  }
}
