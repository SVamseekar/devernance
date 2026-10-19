package os.assurance.eu.api.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import os.assurance.eu.api.auth.JwtService;

/**
 * Real authentication gate for the API. Spring Security is intentionally {@code permitAll};
 * this filter must never be bypassed for application routes.
 *
 * <p>Unauthenticated allowlist is intentionally narrow: JWKS, auth token endpoints, and health probes only.
 * Client-supplied {@code X-Tenant-Id} / {@code X-Actor-Id} are never trusted.
 */
@Component
@Order(2)
public class TenantContextFilter extends OncePerRequestFilter {
    static final String API_KEY_HEADER = "X-Api-Key";
    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";

    /**
     * Paths that skip credential checks. Keep health, auth, OAuth start/callback, and JWKS only.
     * Subpaths under {@code /actuator/health} (liveness/readiness) are also allowed.
     * OAuth routes are prefix-matched under {@code /auth/oauth/}.
     */
    static final Set<String> UNAUTHENTICATED_PATHS = Set.of(
        "/.well-known/jwks.json",
        "/auth/login",
        "/auth/refresh",
        "/auth/logout",
        "/auth/accept-invite",
        "/actuator/health");

    private final ApiKeyJpaRepository apiKeys;
    private final TenantContext tenantContext;
    private final JwtService jwtService;
    private final ApiKeyUsageRecorder apiKeyUsage;
    private final TenantStatusCache tenantStatus;

    public TenantContextFilter(
            ApiKeyJpaRepository apiKeys,
            TenantContext tenantContext,
            JwtService jwtService,
            ApiKeyUsageRecorder apiKeyUsage,
            TenantStatusCache tenantStatus) {
        this.apiKeys = apiKeys;
        this.tenantContext = tenantContext;
        this.jwtService = jwtService;
        this.apiKeyUsage = apiKeyUsage;
        this.tenantStatus = tenantStatus;
    }

    static boolean isUnauthenticatedPath(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if (UNAUTHENTICATED_PATHS.contains(requestUri) || requestUri.startsWith("/actuator/health")) {
            return true;
        }
        if (requestUri.startsWith("/auth/invites/")) {
            return true;
        }
        if (requestUri.equals("/api/v1/billing/webhooks/dodo")) {
            return true;
        }
        // Plan 08: free no-signup tools. Read-only, nothing persisted, rate limited per IP.
        if (requestUri.startsWith("/api/public/")) {
            return true;
        }
        if (requestUri.equals("/auth/signup") || requestUri.startsWith("/auth/verify-email")
            || requestUri.startsWith("/auth/password/") || requestUri.equals("/auth/demo")) {
            return true;
        }
        // Part 4: Google/Microsoft OAuth start + callback must be reachable without a session.
        return requestUri.startsWith("/auth/oauth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        if (isUnauthenticatedPath(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKeyHeader = request.getHeader(API_KEY_HEADER);
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            authenticateWithApiKey(apiKeyHeader, request, response, filterChain);
            return;
        }

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            authenticateWithBearerToken(authorizationHeader.substring(BEARER_PREFIX.length()), request, response, filterChain);
            return;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or unsupported authentication");
    }

    private void authenticateWithApiKey(
            String apiKeyHeader,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        boolean legacy = apiKeyHeader.matches("^[0-9a-fA-F-]{36}$");
        // dvn_ since the Devernance rename; aos_ keys issued before it stay valid.
        boolean modern = apiKeyHeader.matches("^(dvn|aos)_[A-Za-z0-9_-]{43}$");
        if (!legacy && !modern) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + API_KEY_HEADER);
            return;
        }
        ApiKeyEntity key = apiKeys.findByKeyHash(ApiKeyHasher.sha256Hex(apiKeyHeader)).orElse(null);
        if (key == null || key.revoked()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown or revoked API key");
            return;
        }
        if (!tenantStatus.isActive(key.tenantId())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Workspace is no longer active");
            return;
        }
        apiKeyUsage.touch(key.id());
        runAs(key.tenantId(), key.userId(), request, response, filterChain);
    }

    private void authenticateWithBearerToken(
            String token,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var claims = jwtService.verifyAccessToken(token);
        if (claims.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired access token");
            return;
        }
        if (!tenantStatus.isActive(claims.get().tenantId())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Workspace is no longer active");
            return;
        }
        runAs(claims.get().tenantId(), claims.get().userId(), request, response, filterChain);
    }

    private void runAs(
            UUID tenantId,
            UUID userId,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        tenantContext.setOverrides(tenantId, userId);
        MDC.put("tenantId", tenantId.toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            tenantContext.clearOverrides();
        }
    }
}
