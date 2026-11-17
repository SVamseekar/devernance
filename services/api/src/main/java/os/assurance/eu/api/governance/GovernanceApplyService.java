package os.assurance.eu.api.governance;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemController;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.system.CreateAiSystemRequest;
import os.assurance.eu.api.system.UpdateAiSystemRequest;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

/**
 * `devernance apply`: makes the stored governance state equal to the manifests sent. Declarative like
 * `kubectl apply`: sending the same manifest twice changes nothing; sending a changed one adds a new version.
 *
 * <p>An AISystem manifest also registers (or updates) the system in the AI system registry, through the same
 * code path as the dashboard, so plan limits, controls, approval workflows and audit events all apply.
 */
@Service
public class GovernanceApplyService {
  /** What happened to one manifest: {@code created}, {@code configured} (new version) or {@code unchanged}. */
  public record Applied(String kind, String name, int version, String result, UUID systemId, String contentSha256) {
  }

  public record ApplyResponse(List<Applied> resources, List<GovernanceFinding> findings) {
  }

  private final GovernanceResourceJpaRepository resources;
  private final GovernanceRegistry registry;
  private final AiSystemController systems;
  private final AiSystemRepository systemRepository;
  private final TenantAuthorizationService authorization;
  private final TenantContext tenantContext;
  private final AuditService audit;
  private final Clock clock;

  public GovernanceApplyService(
      GovernanceResourceJpaRepository resources,
      GovernanceRegistry registry,
      AiSystemController systems,
      AiSystemRepository systemRepository,
      TenantAuthorizationService authorization,
      TenantContext tenantContext,
      AuditService audit,
      Clock clock) {
    this.resources = resources;
    this.registry = registry;
    this.systems = systems;
    this.systemRepository = systemRepository;
    this.authorization = authorization;
    this.tenantContext = tenantContext;
    this.audit = audit;
    this.clock = clock;
  }

  /** All or nothing: one invalid manifest, and nothing in the request is applied. */
  @Transactional
  public ApplyResponse apply(List<Map<String, Object>> rawManifests) {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);
    List<String> errors = new ArrayList<>();
    List<GovernanceManifest> manifests = GovernanceManifest.parseAll(rawManifests, errors);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("\n", errors));
    }
    // Systems first, so a policy in the same request can target a system that is new in it.
    manifests = manifests.stream()
        .sorted(Comparator.comparing(m -> m.kind() == GovernanceResourceKind.AI_SYSTEM ? 0 : 1))
        .toList();
    List<Applied> applied = new ArrayList<>();
    List<UUID> touchedSystems = new ArrayList<>();
    for (GovernanceManifest manifest : manifests) {
      Applied result = manifest.kind() == GovernanceResourceKind.AI_SYSTEM
          ? applySystem(manifest) : applyPolicy(manifest);
      applied.add(result);
      if (!touchedSystems.contains(result.systemId())) {
        touchedSystems.add(result.systemId());
      }
    }
    resources.flush();
    List<GovernanceFinding> findings = new ArrayList<>();
    for (UUID systemId : touchedSystems) {
      GovernanceResourceEntity system = registry.bySystemId(systemId).orElseThrow();
      PolicySpec policy = registry.activePolicy(systemId).map(GovernanceRegistry::policySpec).orElse(null);
      findings.addAll(GovernanceValidator.validate(system.name(), GovernanceRegistry.systemSpec(system), policy));
    }
    return new ApplyResponse(applied, findings);
  }

  private Applied applySystem(GovernanceManifest manifest) {
    UUID tenantId = tenantContext.tenantId();
    String sha = manifest.contentSha256();
    Optional<GovernanceResourceEntity> existing = resources.findByTenantIdAndKindAndNameAndActiveTrue(
        tenantId, GovernanceResourceKind.AI_SYSTEM, manifest.name());
    if (existing.isPresent()) {
      GovernanceResourceEntity current = existing.get();
      if (manifest.registrySystemId() != null && !manifest.registrySystemId().equals(current.systemId())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "AISystem/" + manifest.name()
            + " is already bound to system " + current.systemId() + "; metadata.systemId cannot move it");
      }
      if (current.contentSha256().equals(sha)) {
        return new Applied(manifest.kind().manifestKind(), manifest.name(), current.version(), "unchanged",
            current.systemId(), sha);
      }
      systems.updateSystem(current.systemId(), registryUpdate(manifest.system()));
      current.supersede();
      resources.save(current);
      return save(manifest, current.systemId(), sha, "configured");
    }
    UUID systemId;
    if (manifest.registrySystemId() != null) {
      systemId = manifest.registrySystemId();
      AiSystem registered = systemRepository.findById(systemId).orElseThrow(() -> new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "AISystem/" + manifest.name() + ": metadata.systemId " + manifest.registrySystemId()
              + " is not a registered system in this workspace"));
      registry.bySystemId(registered.id()).ifPresent(other -> {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "System " + registered.id()
            + " is already governed as AISystem/" + other.name());
      });
      systems.updateSystem(systemId, registryUpdate(manifest.system()));
    } else {
      SystemSpec spec = manifest.system();
      systemId = systems.createSystem(new CreateAiSystemRequest(
          manifest.name(), spec.owner(), spec.purpose(), spec.riskClass(), spec.riskBasis(), spec.deploymentRegion(),
          null, null, null, null, spec.modelProvider(), spec.modelName(), spec.modelVersion(), null, spec.sector(),
          null, null)).id();
    }
    return save(manifest, systemId, sha, "created");
  }

  /** Only what the manifest declares is sent; the registry keeps everything else as it is. */
  private static UpdateAiSystemRequest registryUpdate(SystemSpec spec) {
    return new UpdateAiSystemRequest(
        null, spec.owner(), spec.purpose(), spec.riskClass(), spec.riskBasis(), spec.deploymentRegion(),
        null, null, null, null, spec.modelProvider(), spec.modelName(), spec.modelVersion(), null, spec.sector(),
        null, null, null, null, null, null);
  }

  private Applied applyPolicy(GovernanceManifest manifest) {
    UUID tenantId = tenantContext.tenantId();
    String sha = manifest.contentSha256();
    String target = manifest.policy().targetSystem();
    GovernanceResourceEntity system = resources.findByTenantIdAndKindAndNameAndActiveTrue(
            tenantId, GovernanceResourceKind.AI_SYSTEM, target)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GovernancePolicy/" + manifest.name()
            + ": spec.target.system: no AISystem named '" + target + "' has been applied"));
    Optional<GovernanceResourceEntity> existing = resources.findByTenantIdAndKindAndNameAndActiveTrue(
        tenantId, GovernanceResourceKind.GOVERNANCE_POLICY, manifest.name());
    if (existing.isPresent() && existing.get().contentSha256().equals(sha)
        && existing.get().systemId().equals(system.systemId())) {
      return new Applied(manifest.kind().manifestKind(), manifest.name(), existing.get().version(), "unchanged",
          system.systemId(), sha);
    }
    // A system has one active policy: the newly applied one replaces whichever was active for it.
    List<GovernanceResourceEntity> replaced = new ArrayList<>(resources.findAllByTenantIdAndSystemIdAndKindAndActiveTrue(
        tenantId, system.systemId(), GovernanceResourceKind.GOVERNANCE_POLICY));
    existing.filter(current -> replaced.stream().noneMatch(r -> r.id().equals(current.id()))).ifPresent(replaced::add);
    for (GovernanceResourceEntity old : replaced) {
      old.supersede();
      resources.save(old);
    }
    boolean firstVersion = resources.latestVersion(tenantId, GovernanceResourceKind.GOVERNANCE_POLICY, manifest.name()) == 0;
    return save(manifest, system.systemId(), sha, firstVersion ? "created" : "configured");
  }

  private Applied save(GovernanceManifest manifest, UUID systemId, String sha, String result) {
    UUID tenantId = tenantContext.tenantId();
    resources.flush();
    int version = resources.latestVersion(tenantId, manifest.kind(), manifest.name()) + 1;
    GovernanceResourceEntity saved = resources.save(new GovernanceResourceEntity(
        UUID.randomUUID(), tenantId, systemId, manifest.kind(), manifest.name(), version, manifest.spec(), sha,
        tenantContext.actorId(), clock.instant()));
    audit.append(systemId, "governance.resource_applied", "governance_resource", saved.id().toString(), Map.of(
        "kind", manifest.kind().manifestKind(),
        "name", manifest.name(),
        "version", version,
        "contentSha256", sha,
        "result", result));
    return new Applied(manifest.kind().manifestKind(), manifest.name(), version, result, systemId, sha);
  }
}
