package os.assurance.eu.api.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.tenant.TenantContext;

/**
 * Looks up the applied manifests of the current workspace: the active AISystem resource for a name, and the
 * active GovernancePolicy for a system. Every query is scoped to the caller's tenant.
 */
@Service
public class GovernanceRegistry {
  private final GovernanceResourceJpaRepository resources;
  private final TenantContext tenantContext;

  public GovernanceRegistry(GovernanceResourceJpaRepository resources, TenantContext tenantContext) {
    this.resources = resources;
    this.tenantContext = tenantContext;
  }

  /** The active AISystem resource named {@code nameOrId} (its manifest name, or its registry system id). */
  public Optional<GovernanceResourceEntity> findSystem(String nameOrId) {
    if (nameOrId == null || nameOrId.isBlank()) {
      return Optional.empty();
    }
    UUID tenantId = tenantContext.tenantId();
    String key = nameOrId.trim();
    Optional<GovernanceResourceEntity> byName =
        resources.findByTenantIdAndKindAndNameAndActiveTrue(tenantId, GovernanceResourceKind.AI_SYSTEM, key);
    if (byName.isPresent()) {
      return byName;
    }
    try {
      return bySystemId(UUID.fromString(key));
    } catch (IllegalArgumentException notAnId) {
      return Optional.empty();
    }
  }

  public GovernanceResourceEntity requireSystem(String nameOrId) {
    return findSystem(nameOrId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
        "No governed AI system named '" + nameOrId + "'. Apply an AISystem manifest first."));
  }

  public Optional<GovernanceResourceEntity> bySystemId(UUID systemId) {
    return resources.findAllByTenantIdAndSystemIdAndKindAndActiveTrue(
        tenantContext.tenantId(), systemId, GovernanceResourceKind.AI_SYSTEM).stream().findFirst();
  }

  public List<GovernanceResourceEntity> systems() {
    return resources.findAllByTenantIdAndKindAndActiveTrueOrderByNameAsc(
        tenantContext.tenantId(), GovernanceResourceKind.AI_SYSTEM);
  }

  /** The one active policy for a system, if a policy targets it. */
  public Optional<GovernanceResourceEntity> activePolicy(UUID systemId) {
    return resources.findAllByTenantIdAndSystemIdAndKindAndActiveTrue(
        tenantContext.tenantId(), systemId, GovernanceResourceKind.GOVERNANCE_POLICY).stream().findFirst();
  }

  /** The checked spec of a stored AISystem resource. Stored specs passed the same checks when applied. */
  public static SystemSpec systemSpec(GovernanceResourceEntity resource) {
    List<String> errors = new ArrayList<>();
    SystemSpec spec = SystemSpec.parse(resource.spec(), errors);
    if (!errors.isEmpty()) {
      throw new IllegalStateException("Stored AISystem " + resource.name() + " v" + resource.version()
          + " no longer parses: " + errors);
    }
    return spec;
  }

  /** The checked spec of a stored GovernancePolicy resource. */
  public static PolicySpec policySpec(GovernanceResourceEntity resource) {
    List<String> errors = new ArrayList<>();
    PolicySpec spec = PolicySpec.parse(resource.spec(), errors);
    if (!errors.isEmpty()) {
      throw new IllegalStateException("Stored GovernancePolicy " + resource.name() + " v" + resource.version()
          + " no longer parses: " + errors);
    }
    return spec;
  }
}
