package os.assurance.eu.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.core.env.Environment;
import os.assurance.eu.api.eval.EvalDataset;
import static os.assurance.eu.api.tenant.ApiKeyHasher.sha256Hex;
import os.assurance.eu.api.eval.EvalDatasetEntity;
import os.assurance.eu.api.eval.EvalDatasetJpaRepository;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemJpaRepository;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.DataContractStatus;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.ReleaseGateService;
import os.assurance.eu.api.system.RiskClass;
import os.assurance.eu.api.tenant.ApiKeyEntity;
import os.assurance.eu.api.tenant.ApiKeyJpaRepository;
import os.assurance.eu.api.tenant.DemoCredentialGuard;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;
import os.assurance.eu.api.workflow.ApprovalWorkflowRepository;
import os.assurance.eu.api.workflow.ApprovalWorkflowService;
import os.assurance.eu.api.workflow.WorkflowTrigger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class BootstrapData implements CommandLineRunner {
  private final TenantJpaRepository tenants;
  private final UserJpaRepository users;
  private final ApiKeyJpaRepository apiKeyRepo;
  private final AiSystemJpaRepository systemStore;
  private final AiSystemRepository systems;
  private final EvalDatasetJpaRepository evalDatasets;
  private final ReleaseGateService releaseGateService;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final ApprovalWorkflowRepository approvalWorkflows;
  private final Environment environment;
  private final TenantContext tenantContext;
  private final boolean seedDemoUsers;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

  public BootstrapData(
      TenantJpaRepository tenants,
      UserJpaRepository users,
      ApiKeyJpaRepository apiKeyRepo,
      AiSystemJpaRepository systemStore,
      AiSystemRepository systems,
      EvalDatasetJpaRepository evalDatasets,
      ReleaseGateService releaseGateService,
      ApprovalWorkflowService approvalWorkflowService,
      ApprovalWorkflowRepository approvalWorkflows,
      Environment environment,
      TenantContext tenantContext,
      @org.springframework.beans.factory.annotation.Value("${assurance.bootstrap.seed-demo-users:false}")
      boolean seedDemoUsers) {
    this.tenants = tenants;
    this.users = users;
    this.apiKeyRepo = apiKeyRepo;
    this.systemStore = systemStore;
    this.systems = systems;
    this.evalDatasets = evalDatasets;
    this.releaseGateService = releaseGateService;
    this.approvalWorkflowService = approvalWorkflowService;
    this.approvalWorkflows = approvalWorkflows;
    this.environment = environment;
    this.tenantContext = tenantContext;
    this.seedDemoUsers = seedDemoUsers;
  }

  @Override
  @Transactional
  public void run(String... args) {
    tenantContext.setOverrides(TenantContext.DEFAULT_TENANT_ID, TenantContext.SYSTEM_USER_ID);
    try {
      seedBootstrapData();
    } finally {
      tenantContext.clearOverrides();
    }
  }

  private void seedBootstrapData() {
    Instant now = Instant.now();
    tenants.findById(TenantContext.DEFAULT_TENANT_ID)
        .orElseGet(() -> tenants.save(new TenantEntity(
            TenantContext.DEFAULT_TENANT_ID,
            "MVP Tenant",
            "starter",
            "EU",
            now)));
    // Background jobs audit as this actor; it exists in every environment so audit FKs hold.
    users.findById(TenantContext.SYSTEM_USER_ID)
        .orElseGet(() -> users.save(new UserEntity(
            TenantContext.SYSTEM_USER_ID,
            TenantContext.DEFAULT_TENANT_ID,
            TenantContext.SYSTEM_USER_EMAIL,
            UserRole.AUDITOR,
            now)));
    if (!seedDemoUsers) {
      // Not demo data: backfills approval workflows for real systems in existing databases.
      ensureApprovalWorkflows();
      return;
    }
    markVerified(users.findById(TenantContext.DEFAULT_USER_ID)
        .orElseGet(() -> users.save(new UserEntity(
            TenantContext.DEFAULT_USER_ID,
            TenantContext.DEFAULT_TENANT_ID,
            "compliance@example.com",
            UserRole.COMPLIANCE_OFFICER,
            passwordEncoder.encode(DemoCredentialGuard.DEV_PASSWORD),
            now))), now);
    seedUser("00000000-0000-0000-0000-000000000102",
        "engineering@example.com", UserRole.AI_ENGINEERING_LEAD, now);
    seedUser("00000000-0000-0000-0000-000000000103",
        "auditor@example.com", UserRole.AUDITOR, now);
    seedUser("00000000-0000-0000-0000-000000000104",
        "legal@example.com", UserRole.LEGAL_COUNSEL, now);
    seedUser("00000000-0000-0000-0000-000000000105",
        "admin@example.com", UserRole.ADMIN, now);
    // Only seed the fixed dev/test API key when NOT running against real Postgres
    if (!Arrays.asList(environment.getActiveProfiles()).contains("postgres")) {
      UUID defaultApiKey = UUID.fromString("00000000-0000-0000-0000-000000000a01");
      apiKeyRepo.findById(defaultApiKey)
          .orElseGet(() -> apiKeyRepo.save(new ApiKeyEntity(
              defaultApiKey,
              sha256Hex(defaultApiKey.toString()),
              TenantContext.DEFAULT_TENANT_ID,
              TenantContext.DEFAULT_USER_ID,
              now)));
    }
    if (!evalDatasets.existsByTenantIdAndNameAndVersion(
        TenantContext.DEFAULT_TENANT_ID,
        "golden-eu-claims-v4",
        "2026-06")) {
      evalDatasets.save(new EvalDatasetEntity(
          TenantContext.DEFAULT_TENANT_ID,
          new EvalDataset(
              UUID.randomUUID(),
              "golden-eu-claims-v4",
              "2026-06",
              240,
              true,
              now)));
    }

    if (!systemStore.existsByTenantId(TenantContext.DEFAULT_TENANT_ID)) {
      seed(
          "Claims Triage AI",
          "Insurance Ops",
          "Prioritize and route insurance claims",
          RiskClass.HIGH,
          "Eligibility and access to essential private services",
          72,
          78,
          DataContractStatus.BREACH,
          List.of("Human oversight SOP missing", "Bias eval below threshold"));
      seed(
          "Support RAG Copilot",
          "Customer Success",
          "Answer customer support questions with cited sources",
          RiskClass.LIMITED,
          "Customer-facing assistant with transparency duty",
          88,
          86,
          DataContractStatus.HEALTHY,
          List.of("Update chatbot disclosure copy"));
    }
    boolean dogfoodPresent = systems.findAll().stream()
        .anyMatch(system -> "Devernance".equals(system.name()));
    if (!dogfoodPresent) {
      seed(
          "Devernance",
          "MSV AI Labs",
          "Governance control plane with cited evidence RAG and assisted obligation mapping for operators of AI systems",
          RiskClass.LIMITED,
          "Internal B2B governance tool; Art. 50-style transparency if operators interact with the assistant. Not a high-risk Annex III use by default — human legal review required.",
          70,
          80,
          DataContractStatus.HEALTHY,
          List.of("Dogfood: run obligation map + conformity dossier on this product"));
    }

    // Lab/demo: open an approval cycle for systems that have never had a workflow
    // (seed path used to skip openCycle; existing DBs may also be missing rows).
    ensureApprovalWorkflows();
  }

  private void ensureApprovalWorkflows() {
    for (AiSystem system : systems.findAll()) {
      if (approvalWorkflows.findAllBySystemId(system.id()).isEmpty()) {
        approvalWorkflowService.openCycle(system, WorkflowTrigger.SYSTEM_CREATED);
      }
    }
  }

  private void seedUser(String id, String email, UserRole role, Instant now) {
    UUID userId = UUID.fromString(id);
    markVerified(users.findById(userId)
        .orElseGet(() -> users.save(new UserEntity(
            userId,
            TenantContext.DEFAULT_TENANT_ID,
            email,
            role,
            passwordEncoder.encode(DemoCredentialGuard.DEV_PASSWORD),
            now))), now);
  }

  /** Seeded dev users have no inbox to confirm, so they start verified. */
  private void markVerified(UserEntity user, Instant now) {
    if (user.emailVerifiedAt() == null) {
      user.markEmailVerified(now);
      users.save(user);
    }
  }

  private void seed(
      String name,
      String owner,
      String purpose,
      RiskClass riskClass,
      String riskBasis,
      int evidenceCoverage,
      int evalScore,
      DataContractStatus dataContractStatus,
      List<String> openGaps) {
    Instant now = Instant.now();
    AiSystem draft = new AiSystem(
        UUID.randomUUID(),
        name,
        owner,
        purpose,
        riskClass,
        riskBasis,
        "EU",
        evidenceCoverage,
        evalScore,
        dataContractStatus,
        ReleaseDecision.REVIEW,
        new ArrayList<>(openGaps),
        null,
        null,
        null,
        List.of(),
        null,
        null,
        List.of(),
        now,
        now);
    ReleaseDecision decision = releaseGateService.calculate(draft).decision();
    AiSystem saved = systems.save(new AiSystem(
        draft.id(),
        draft.name(),
        draft.owner(),
        draft.purpose(),
        draft.riskClass(),
        draft.riskBasis(),
        draft.deploymentRegion(),
        draft.evidenceCoverage(),
        draft.evalScore(),
        draft.dataContractStatus(),
        decision,
        draft.openGaps(),
        draft.vendorName(),
        draft.modelName(),
        draft.modelVersion(),
        draft.dataSources(),
        draft.sector(),
        draft.decisionImpact(),
        draft.affectedUsers(),
        draft.createdAt(),
        draft.updatedAt()));
    approvalWorkflowService.openCycle(saved, WorkflowTrigger.SYSTEM_CREATED);
  }
}
