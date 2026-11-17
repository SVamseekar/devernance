package os.assurance.eu.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyScopeTest {
  private static final String DECISION = "/api/v1/governance/decisions/0b4c2f6e-6a8d-4d8b-9a55-1f6c9c1d2e3f";

  @Test
  void releaseGateKeysOnlyReachTheGateAndReadOnlyGovernanceChecks() {
    ApiKeyScope scope = ApiKeyScope.RELEASE_GATE;
    assertThat(scope.allows("GET", "/api/v1/ci/release-gate")).isTrue();
    assertThat(scope.allows("GET", "/api/v1/governance/release-check")).isTrue();
    assertThat(scope.allows("POST", "/api/v1/governance/validate")).isTrue();
    assertThat(scope.allows("GET", "/api/v1/governance/validate")).isTrue();
    assertThat(scope.allows("POST", "/api/v1/governance/simulate")).isTrue();
    assertThat(scope.allows("POST", "/api/v1/governance/apply")).isFalse();
    assertThat(scope.allows("POST", "/api/v1/governance/authorize")).isFalse();
    assertThat(scope.allows("POST", "/api/v1/ci/release-gate")).isFalse();
    assertThat(scope.allows("GET", "/api/v1/systems")).isFalse();
  }

  @Test
  void runtimeKeysOnlyReachTheRuntimeCalls() {
    ApiKeyScope scope = ApiKeyScope.RUNTIME;
    assertThat(scope.allows("POST", "/api/v1/governance/authorize")).isTrue();
    assertThat(scope.allows("GET", DECISION)).isTrue();
    assertThat(scope.allows("POST", DECISION + "/execution")).isTrue();
    assertThat(scope.allows("POST", "/api/v1/governance/authorizations/verify")).isTrue();
    assertThat(scope.allows("POST", "/api/v1/governance/observations")).isTrue();
    assertThat(scope.allows("GET", "/api/v1/governance/decisions")).isFalse();
    assertThat(scope.allows("POST", "/api/v1/governance/approvals/" + DECISION.substring(DECISION.lastIndexOf('/') + 1)
        + "/approve")).isFalse();
    assertThat(scope.allows("GET", DECISION + "/../../approvals")).isFalse();
    assertThat(scope.allows("GET", "/api/v1/ci/release-gate")).isFalse();
    assertThat(scope.allows("POST", "/api/v1/governance/apply")).isFalse();
    assertThat(scope.allows("GET", null)).isFalse();
  }

  @Test
  void fullKeysReachEverythingTheirRoleAllows() {
    assertThat(ApiKeyScope.FULL.allows("POST", "/api/v1/governance/apply")).isTrue();
  }
}
