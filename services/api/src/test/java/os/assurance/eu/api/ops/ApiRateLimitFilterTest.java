package os.assurance.eu.api.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import os.assurance.eu.api.demo.DemoProperties;
import os.assurance.eu.api.tenant.TenantContext;

class ApiRateLimitFilterTest {
  private final TenantContext tenantContext = new TenantContext();
  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-19T10:00:00Z"));
  private final Clock clock = new Clock() {
    @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    @Override public Instant instant() { return now.get(); }
  };
  private final ApiRateLimitFilter filter = new ApiRateLimitFilter(tenantContext, clock, 3, 1, true);

  private int call(UUID tenantId, UUID actorId, String uri, String ip) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    if (ip != null) {
      request.addHeader("X-Client-IP", ip);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    tenantContext.setOverrides(tenantId, actorId);
    try {
      filter.doFilter(request, response, new MockFilterChain());
    } finally {
      tenantContext.clearOverrides();
    }
    return response.getStatus();
  }

  @Test
  void limitsEachCallerPerMinuteAndResetsNextMinute() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      assertThat(call(tenant, actor, "/api/v1/systems", null)).isEqualTo(200);
    }
    assertThat(call(tenant, actor, "/api/v1/systems", null)).isEqualTo(429);
    // Another caller in the same workspace has its own budget.
    assertThat(call(tenant, UUID.randomUUID(), "/api/v1/systems", null)).isEqualTo(200);

    now.set(now.get().plusSeconds(60));
    assertThat(call(tenant, actor, "/api/v1/systems", null)).isEqualTo(200);
  }

  @Test
  void heavyRequestsShareASmallerBudgetPerWorkspace() throws Exception {
    UUID tenant = UUID.randomUUID();
    assertThat(call(tenant, UUID.randomUUID(), "/api/v1/systems/abc/evidence-pack.pdf", null)).isEqualTo(200);
    assertThat(call(tenant, UUID.randomUUID(), "/api/v1/systems/abc/grc-export", null)).isEqualTo(429);
    assertThat(call(tenant, UUID.randomUUID(), "/api/v1/systems/abc", null)).isEqualTo(200);
  }

  @Test
  void demoVisitorsAreBudgetedByAddressNotTogether() throws Exception {
    UUID demoTenant = DemoProperties.DEMO_TENANT_ID;
    UUID demoUser = DemoProperties.DEMO_USER_ID;
    for (int i = 0; i < 3; i++) {
      assertThat(call(demoTenant, demoUser, "/api/v1/systems", "198.51.100.1")).isEqualTo(200);
    }
    assertThat(call(demoTenant, demoUser, "/api/v1/systems", "198.51.100.1")).isEqualTo(429);
    assertThat(call(demoTenant, demoUser, "/api/v1/systems", "198.51.100.2")).isEqualTo(200);
  }

  @Test
  void leavesNonApiAndUnauthenticatedRequestsAlone() throws Exception {
    MockHttpServletRequest webhook = new MockHttpServletRequest("POST", "/api/v1/billing/webhooks/dodo");
    MockHttpServletResponse response = new MockHttpServletResponse();
    for (int i = 0; i < 10; i++) {
      filter.doFilter(webhook, response, new MockFilterChain());
    }
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
  }

  @Test
  void classifiesHeavyRoutes() {
    assertThat(ApiRateLimitFilter.isExpensive("/api/v1/systems/x/evidence-pack")).isTrue();
    assertThat(ApiRateLimitFilter.isExpensive("/api/v1/evidence/documents/upload")).isTrue();
    assertThat(ApiRateLimitFilter.isExpensive("/api/v1/account/export")).isTrue();
    assertThat(ApiRateLimitFilter.isExpensive("/api/v1/systems/x/release-gate")).isFalse();
  }
}
