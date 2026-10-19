package os.assurance.eu.api.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import os.assurance.eu.api.auth.SlidingWindowRateLimiter;
import os.assurance.eu.api.tenant.TenantContext;

/**
 * Keeps the public demo workspace read-only. Runs after {@code TenantContextFilter} (order 2), which sets the
 * tenant for the duration of the chain, so the tenant is known here.
 */
@Component
@Order(3)
public class DemoReadOnlyFilter extends OncePerRequestFilter {
  static final String CLIENT_IP_HEADER = "X-Client-IP";

  private final TenantContext tenantContext;
  private final SlidingWindowRateLimiter queriesPerClient;
  private final SlidingWindowRateLimiter queriesInTotal;
  private final boolean trustClientIpHeader;

  public DemoReadOnlyFilter(
      TenantContext tenantContext,
      @Value("${assurance.demo.query-limit-per-15m:20}") int queryLimit,
      @Value("${assurance.demo.query-limit-global-per-15m:300}") int globalQueryLimit,
      @Value("${assurance.security.trust-client-ip-header:false}") boolean trustClientIpHeader,
      Clock clock) {
    this.tenantContext = tenantContext;
    this.queriesPerClient = new SlidingWindowRateLimiter(queryLimit, Duration.ofMinutes(15), clock);
    this.queriesInTotal = new SlidingWindowRateLimiter(globalQueryLimit, Duration.ofMinutes(15), clock);
    this.trustClientIpHeader = trustClientIpHeader;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String method = request.getMethod();
    boolean write = !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
    boolean allowedWrite = "POST".equals(method) && "/api/v1/evidence/query".equals(request.getRequestURI());
    // The CI gate is meant for pipelines holding an API key, which the demo never has. Each call is audited, so
    // leaving it open would only let visitors grow the shared demo trail.
    if (request.getRequestURI().startsWith("/api/v1/ci/") && isDemoTenant()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "The demo workspace is read-only. Sign up to make changes.");
      return;
    }
    if (write && isDemoTenant()) {
      if (!allowedWrite) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "The demo workspace is read-only. Sign up to make changes.");
        return;
      }
      // The one allowed write stores a question in the shared workspace, so cap it per client and, in case
      // client addresses are spoofed or rotated, for the whole workspace.
      if (!queriesPerClient.tryAcquire(clientIp(request)) || !queriesInTotal.tryAcquire("demo")) {
        response.setStatus(429);
        response.setHeader("Retry-After", "900");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"too_many_requests\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private String clientIp(HttpServletRequest request) {
    if (trustClientIpHeader) {
      String forwarded = request.getHeader(CLIENT_IP_HEADER);
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.trim();
      }
    }
    return request.getRemoteAddr();
  }

  private boolean isDemoTenant() {
    try {
      return DemoProperties.DEMO_TENANT_ID.equals(tenantContext.tenantId());
    } catch (IllegalStateException unauthenticated) {
      return false;
    }
  }
}
