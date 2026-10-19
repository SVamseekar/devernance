package os.assurance.eu.api.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import os.assurance.eu.api.tenant.PlatformProperties;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;

/**
 * Metrics, Prometheus and build info describe the whole platform, not one workspace, so only an admin of the
 * platform operator tenant may read them. Health probes stay public. Runs after {@code TenantContextFilter}
 * (order 2), which has already rejected unauthenticated callers. Everyone else gets 404, as if the endpoint
 * did not exist.
 */
@Component
@Order(4)
public class OperatorActuatorFilter extends OncePerRequestFilter {
  private final TenantContext tenantContext;
  private final PlatformProperties platform;
  private final UserJpaRepository users;

  public OperatorActuatorFilter(TenantContext tenantContext, PlatformProperties platform, UserJpaRepository users) {
    this.tenantContext = tenantContext;
    this.platform = platform;
    this.users = users;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    boolean actuator = "/actuator".equals(uri) || uri.startsWith("/actuator/");
    return !actuator || uri.equals("/actuator/health") || uri.startsWith("/actuator/health/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!isOperatorAdmin()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean isOperatorAdmin() {
    UUID tenantId;
    UUID actorId;
    try {
      tenantId = tenantContext.tenantId();
      actorId = tenantContext.actorId();
    } catch (IllegalStateException unauthenticated) {
      return false;
    }
    return tenantId.equals(platform.getOperatorTenantId())
        && users.findByIdAndTenantId(actorId, tenantId).map(u -> u.role() == UserRole.ADMIN).orElse(false);
  }
}
