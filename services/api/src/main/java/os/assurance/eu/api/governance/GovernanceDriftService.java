package os.assurance.eu.api.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

/**
 * Reconciliation: compares what a deployment reports it is running (the observed state) with the applied
 * AISystem manifest and active policy (the approved state). A difference is governance drift.
 *
 * <p>Drift is recomputed on every read against the currently approved state. So approving a change (applying
 * the updated manifest) clears the drift without the deployment having to report again.
 *
 * <p>Only what a deployment reports is compared. A property it does not report is unknown, not in sync.
 */
@Service
public class GovernanceDriftService {
  static final Pattern ENVIRONMENT = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");
  public static final String DEFAULT_ENVIRONMENT = "production";

  /** What a deployment reports. Every property is optional, but at least one must be present. */
  public record ObservationRequest(
      String system,
      String environment,
      String model,
      String promptHash,
      List<String> tools,
      Integer policyVersion,
      String source) {
  }

  public record Difference(String property, String approved, String observed) {
  }

  /**
   * {@code modelApproved} is null when the model did not change or the policy has no approved list.
   * {@code required} is true when anything drifted: the approval was given for a different state.
   */
  public record Reassessment(boolean required, Boolean modelApproved, List<String> notes) {
  }

  public record DriftReport(
      String system,
      UUID systemId,
      String environment,
      /** False when this environment has never reported. Then nothing is known and {@code inSync} is false. */
      boolean observed,
      boolean inSync,
      List<Difference> differences,
      Reassessment reassessment,
      Map<String, Object> observedState,
      Instant observedAt,
      String source) {
  }

  private final GovernanceObservationJpaRepository observations;
  private final GovernanceRegistry registry;
  private final TenantAuthorizationService authorization;
  private final TenantContext tenantContext;
  private final AuditService audit;
  private final Clock clock;

  public GovernanceDriftService(
      GovernanceObservationJpaRepository observations,
      GovernanceRegistry registry,
      TenantAuthorizationService authorization,
      TenantContext tenantContext,
      AuditService audit,
      Clock clock) {
    this.observations = observations;
    this.registry = registry;
    this.authorization = authorization;
    this.tenantContext = tenantContext;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public DriftReport record(ObservationRequest request) {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD);
    GovernanceResourceEntity system = registry.requireSystem(request.system());
    String environment = environment(request.environment());
    Map<String, Object> observed = new LinkedHashMap<>();
    if (request.model() != null) {
      observed.put("model", bounded("model", request.model(), 256));
    }
    if (request.promptHash() != null) {
      observed.put("promptHash", bounded("promptHash", request.promptHash(), 128));
    }
    if (request.tools() != null) {
      if (request.tools().size() > 200) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tools: at most 200 entries");
      }
      List<String> tools = new ArrayList<>();
      for (String tool : request.tools()) {
        tools.add(bounded("tools", tool, 128));
      }
      observed.put("tools", new ArrayList<>(new TreeSet<>(tools)));
    }
    if (request.policyVersion() != null) {
      observed.put("policyVersion", request.policyVersion());
    }
    if (observed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Report at least one of model, promptHash, tools, policyVersion");
    }
    String source = request.source() == null || request.source().isBlank()
        ? "unspecified" : bounded("source", request.source(), 128);

    UUID tenantId = tenantContext.tenantId();
    Optional<GovernanceObservationEntity> previous = latest(system.systemId(), environment);
    Instant now = clock.instant();
    List<Difference> differences = differences(system, registry.activePolicy(system.systemId()).orElse(null), observed);
    GovernanceObservationEntity saved = observations.save(new GovernanceObservationEntity(
        UUID.randomUUID(), tenantId, system.systemId(), environment, observed, differences.isEmpty(), source,
        tenantContext.actorId(), now));

    boolean wasInSync = previous.map(GovernanceObservationEntity::inSync).orElse(true);
    boolean changed = previous.map(p -> !p.observed().equals(observed)).orElse(true);
    if (!differences.isEmpty() && (wasInSync || changed)) {
      audit.append(system.systemId(), "governance.drift_detected", "governance_observation", saved.id().toString(),
          Map.of("environment", environment,
              "properties", String.join(",", differences.stream().map(Difference::property).toList())));
    } else if (differences.isEmpty() && !wasInSync) {
      audit.append(system.systemId(), "governance.drift_resolved", "governance_observation", saved.id().toString(),
          Map.of("environment", environment));
    }
    return report(system, environment);
  }

  /** The current drift of one environment of one system. */
  @Transactional(readOnly = true)
  public DriftReport report(GovernanceResourceEntity system, String environment) {
    Optional<GovernanceObservationEntity> latest = latest(system.systemId(), environment);
    if (latest.isEmpty()) {
      return new DriftReport(system.name(), system.systemId(), environment, false, false, List.of(),
          new Reassessment(false, null, List.of("This environment has not reported its state.")), Map.of(), null, null);
    }
    GovernanceObservationEntity observation = latest.get();
    GovernanceResourceEntity policy = registry.activePolicy(system.systemId()).orElse(null);
    List<Difference> differences = differences(system, policy, observation.observed());
    return new DriftReport(system.name(), system.systemId(), environment, true, differences.isEmpty(), differences,
        reassess(differences, policy), observation.observed(), observation.createdAt(), observation.source());
  }

  /** The current drift of every environment that has reported, for one system or (null) for all of them. */
  @Transactional(readOnly = true)
  public List<DriftReport> reports(String systemNameOrId) {
    List<GovernanceResourceEntity> systems = systemNameOrId == null || systemNameOrId.isBlank()
        ? registry.systems() : List.of(registry.requireSystem(systemNameOrId));
    List<DriftReport> reports = new ArrayList<>();
    for (GovernanceResourceEntity system : systems) {
      for (String environment : observations.findEnvironments(tenantContext.tenantId(), system.systemId())) {
        reports.add(report(system, environment));
      }
    }
    return reports;
  }

  /**
   * The first environment of the system whose latest report differs from the approved state, if any.
   *
   * <p>Used by {@code enforcement.onDrift: block}. It looks at every environment on purpose: the environment named
   * in an authorize request comes from the agent, so it is a label for the record, never a way to step around drift
   * reported somewhere else.
   */
  @Transactional(readOnly = true)
  public Optional<String> driftedEnvironment(GovernanceResourceEntity system) {
    for (String environment : observations.findEnvironments(tenantContext.tenantId(), system.systemId())) {
      DriftReport report = report(system, environment);
      if (report.observed() && !report.inSync()) {
        return Optional.of(environment);
      }
    }
    return Optional.empty();
  }

  static String environment(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_ENVIRONMENT;
    }
    String environment = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if (!ENVIRONMENT.matcher(environment).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "environment: use lowercase letters, digits, _ and - (at most 64 characters)");
    }
    return environment;
  }

  private Optional<GovernanceObservationEntity> latest(UUID systemId, String environment) {
    return observations.findByTenantIdAndSystemIdAndEnvironmentOrderByCreatedAtDesc(
        tenantContext.tenantId(), systemId, environment, Limit.of(1)).stream().findFirst();
  }

  private static List<Difference> differences(
      GovernanceResourceEntity system, GovernanceResourceEntity policy, Map<String, Object> observed) {
    SystemSpec approved = GovernanceRegistry.systemSpec(system);
    List<Difference> differences = new ArrayList<>();
    if (observed.get("model") instanceof String model && approved.modelRef() != null
        && !modelMatches(approved, model)) {
      differences.add(new Difference("model", approved.modelRef(), model));
    }
    if (observed.get("promptHash") instanceof String promptHash && approved.promptHash() != null
        && !approved.promptHash().equals(promptHash)) {
      differences.add(new Difference("promptHash", approved.promptHash(), promptHash));
    }
    if (observed.get("tools") instanceof List<?> tools) {
      Set<String> running = new TreeSet<>();
      tools.forEach(tool -> running.add(String.valueOf(tool)));
      Set<String> declared = new TreeSet<>(approved.tools());
      if (!running.equals(declared)) {
        differences.add(new Difference("tools", String.join(", ", declared), String.join(", ", running)));
      }
    }
    if (observed.get("policyVersion") instanceof Number version) {
      Integer active = policy == null ? null : policy.version();
      if (active == null || active != version.intValue()) {
        differences.add(new Difference("policyVersion", active == null ? "none" : String.valueOf(active),
            String.valueOf(version.intValue())));
      }
    }
    return differences;
  }

  /** An approved model without a version approves every version of that model. */
  private static boolean modelMatches(SystemSpec approved, String observed) {
    if (approved.modelRef().equals(observed)) {
      return true;
    }
    return approved.modelVersion() == null && observed.startsWith(approved.modelName() + "@");
  }

  private static Reassessment reassess(List<Difference> differences, GovernanceResourceEntity policy) {
    if (differences.isEmpty()) {
      return new Reassessment(false, null, List.of());
    }
    List<String> notes = new ArrayList<>();
    Boolean modelApproved = null;
    for (Difference difference : differences) {
      switch (difference.property()) {
        case "model" -> {
          PolicySpec spec = policy == null ? null : GovernanceRegistry.policySpec(policy);
          if (spec != null && !spec.approvedModels().isEmpty()) {
            modelApproved = spec.approvesModel(difference.observed());
            notes.add(modelApproved
                ? "The running model is on the approved list, but the system was approved with "
                    + difference.approved() + ". Re-run evaluations, then apply the updated AISystem manifest."
                : "The running model is not on the approved list. Roll back, or have it approved and added.");
          } else {
            notes.add("The running model differs from the approved one. Re-run evaluations, then apply the "
                + "updated AISystem manifest.");
          }
        }
        case "promptHash" -> notes.add("The running prompt differs from the approved one. Review the change, "
            + "then apply the updated AISystem manifest.");
        case "tools" -> notes.add("The running tools differ from the declared ones. Every new tool needs a "
            + "policy mapping and rule; run validate after updating the manifests.");
        default -> notes.add("The deployment enforces policy version " + difference.observed()
            + " but version " + difference.approved() + " is active. Redeploy or refresh its policy.");
      }
    }
    return new Reassessment(true, modelApproved, notes);
  }

  private static String bounded(String field, String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          field + ": must be a non-empty string of at most " + max + " characters");
    }
    return value.trim();
  }
}
