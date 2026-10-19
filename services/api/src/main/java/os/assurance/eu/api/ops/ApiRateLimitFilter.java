package os.assurance.eu.api.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import os.assurance.eu.api.demo.DemoProperties;
import os.assurance.eu.api.tenant.TenantContext;

/**
 * Per-caller request budget for the authenticated API, so one key, session or demo visitor cannot starve the
 * others. Requests that start heavy work (evgraph scans, PDF and zip exports, uploads, mapping) have a smaller
 * budget per workspace. The shared demo workspace is budgeted per visitor address instead, so one visitor cannot
 * use up the demo for everyone. Runs after {@code TenantContextFilter} (order 2). In-memory, one API instance;
 * Cloudflare adds per-IP edge limits in production.
 */
@Component
@Order(5)
public class ApiRateLimitFilter extends OncePerRequestFilter {
  static final String CLIENT_IP_HEADER = "X-Client-IP";
  static final List<Pattern> EXPENSIVE = List.of(
      Pattern.compile("^/api/v1/systems/[^/]+/evidence-pack(\\.pdf)?$"),
      Pattern.compile("^/api/v1/systems/[^/]+/(grc-export|questionnaire-export)$"),
      Pattern.compile("^/api/v1/systems/[^/]+/certification-readiness/export$"),
      Pattern.compile("^/api/v1/systems/[^/]+/proposals/map$"),
      Pattern.compile("^/api/v1/account/export$"),
      Pattern.compile("^/api/v1/packs/.+$"),
      Pattern.compile("^/api/v1/public-claims/[^/]+/evgraph$"),
      Pattern.compile("^/api/v1/evidence/documents/upload$"));
  private static final int MAX_KEYS = 50_000;

  private final TenantContext tenantContext;
  private final Clock clock;
  private final int perCallerPerMinute;
  private final int expensivePerWorkspacePerMinute;
  private final boolean trustClientIpHeader;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public ApiRateLimitFilter(
      TenantContext tenantContext,
      Clock clock,
      @Value("${assurance.security.api-rate.per-caller-per-minute:600}") int perCallerPerMinute,
      @Value("${assurance.security.api-rate.expensive-per-workspace-per-minute:30}") int expensivePerWorkspacePerMinute,
      @Value("${assurance.security.trust-client-ip-header:false}") boolean trustClientIpHeader) {
    this.tenantContext = tenantContext;
    this.clock = clock;
    this.perCallerPerMinute = perCallerPerMinute;
    this.expensivePerWorkspacePerMinute = expensivePerWorkspacePerMinute;
    this.trustClientIpHeader = trustClientIpHeader;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID tenantId;
    UUID actorId;
    try {
      tenantId = tenantContext.tenantId();
      actorId = tenantContext.actorId();
    } catch (IllegalStateException unauthenticated) {
      chain.doFilter(request, response); // public routes such as the billing webhook
      return;
    }
    boolean demo = DemoProperties.DEMO_TENANT_ID.equals(tenantId);
    String caller = demo ? "demo:" + clientIp(request) : actorId.toString();
    String workspace = demo ? caller : tenantId.toString();
    long minute = clock.millis() / 60_000;
    if (!acquire("all:" + caller, minute, perCallerPerMinute)
        || (isExpensive(request.getRequestURI())
            && !acquire("heavy:" + workspace, minute, expensivePerWorkspacePerMinute))) {
      response.setStatus(429);
      response.setHeader("Retry-After", "60");
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"too_many_requests\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  static boolean isExpensive(String uri) {
    return EXPENSIVE.stream().anyMatch(pattern -> pattern.matcher(uri).matches());
  }

  private boolean acquire(String key, long minute, int limit) {
    if (windows.size() > MAX_KEYS) {
      windows.entrySet().removeIf(entry -> entry.getValue().minute < minute);
    }
    Window window = windows.compute(key, (k, current) ->
        current == null || current.minute != minute ? new Window(minute) : current);
    return window.count.incrementAndGet() <= limit;
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

  private static final class Window {
    final long minute;
    final AtomicInteger count = new AtomicInteger();

    Window(long minute) {
      this.minute = minute;
    }
  }
}
