package os.assurance.eu.api.tenant;

import java.util.List;
import java.util.regex.Pattern;

/**
 * What an API key may do. A key otherwise acts with its creator's role, so keys that live in pipelines and in
 * running agents are kept to the few calls they need.
 */
public enum ApiKeyScope {
  /**
   * For deploy pipelines: read the release gate and run the read-only governance checks. Nothing a leaked key
   * could use to change systems, policies, approvals or evidence.
   */
  RELEASE_GATE,
  /**
   * For a running agent or its enforcement point: ask for authorization decisions, read them back, report the
   * outcome, verify an authorization token and report what the deployment is running. It cannot approve, apply
   * policies or read other records.
   */
  RUNTIME,
  /** Every endpoint the creator's role allows, except minting keys, inviting users or managing the workspace. */
  FULL;

  static final String RELEASE_GATE_PATH = "/api/v1/ci/release-gate";
  private static final String UUID_SEGMENT = "[0-9a-fA-F-]{36}";

  private record Route(String method, Pattern path) {
    static Route of(String method, String regex) {
      return new Route(method, Pattern.compile(regex));
    }

    boolean matches(String requestMethod, String requestPath) {
      return method.equalsIgnoreCase(requestMethod) && path.matcher(requestPath).matches();
    }
  }

  private static final List<Route> RELEASE_GATE_ROUTES = List.of(
      Route.of("GET", "^/api/v1/ci/release-gate$"),
      Route.of("GET", "^/api/v1/governance/release-check$"),
      Route.of("POST", "^/api/v1/governance/validate$"),
      Route.of("GET", "^/api/v1/governance/validate$"),
      Route.of("POST", "^/api/v1/governance/simulate$"));

  private static final List<Route> RUNTIME_ROUTES = List.of(
      Route.of("POST", "^/api/v1/governance/authorize$"),
      Route.of("GET", "^/api/v1/governance/decisions/" + UUID_SEGMENT + "$"),
      Route.of("POST", "^/api/v1/governance/decisions/" + UUID_SEGMENT + "/execution$"),
      Route.of("POST", "^/api/v1/governance/authorizations/verify$"),
      Route.of("POST", "^/api/v1/governance/observations$"));

  public boolean allows(String method, String path) {
    if (path == null) {
      return false;
    }
    return switch (this) {
      case FULL -> true;
      case RELEASE_GATE -> RELEASE_GATE_ROUTES.stream().anyMatch(route -> route.matches(method, path));
      case RUNTIME -> RUNTIME_ROUTES.stream().anyMatch(route -> route.matches(method, path));
    };
  }

  /** What to tell a caller whose key is not allowed to make the call. */
  public String refusal() {
    return switch (this) {
      case RELEASE_GATE -> "This API key may only call GET " + RELEASE_GATE_PATH
          + " and the read-only governance checks; create a full-access key for other calls";
      case RUNTIME -> "This API key may only request, read and report governance authorization decisions; "
          + "create a full-access key for other calls";
      case FULL -> "This API key is not allowed to make this call";
    };
  }
}
