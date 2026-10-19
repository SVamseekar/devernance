package os.assurance.eu.api.audit;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
  private final os.assurance.eu.api.billing.EntitlementService entitlements;
  private final AuditService auditService;
  private final AiSystemRepository systems;
  private final TenantAuthorizationService authorizationService;

  public AuditController(
      AuditService auditService,
      AiSystemRepository systems,
      TenantAuthorizationService authorizationService,
      os.assurance.eu.api.billing.EntitlementService entitlements) {
    this.entitlements = entitlements;
    this.auditService = auditService;
    this.systems = systems;
    this.authorizationService = authorizationService;
  }

  static final int DEFAULT_LIMIT = 500;
  static final int MAX_LIMIT = 1000;

  /**
   * Newest first and bounded: a busy workspace (every CI gate run is audited) can hold far more events than a
   * page should carry. Page back with {@code before}; the workspace export holds the full trail.
   */
  @GetMapping
  public List<AuditEvent> listAuditEvents(
      @RequestParam(required = false) UUID systemId,
      @RequestParam(required = false) Instant before,
      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
    return auditService.recent(systemId, before, Math.max(1, Math.min(limit, MAX_LIMIT)));
  }

  @GetMapping("/verify-chain")
  public AuditChainVerifyResponse verifyChain() {
    return auditService.verifyChain();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AuditEvent appendAuditEvent(@Valid @RequestBody CreateAuditEventRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.COMPLIANCE_OFFICER, UserRole.AI_ENGINEERING_LEAD);
    if (request.systemId() != null && systems.findById(request.systemId()).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found");
    }
    if (request.systemId() != null) {
      entitlements.requireSystemWritable(request.systemId());
    }
    String requested = request.eventType() == null ? "" : request.eventType().trim().toLowerCase(java.util.Locale.ROOT);
    if (!requested.matches("^[a-z0-9_.-]{3,64}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType must match ^[a-z0-9_.-]{3,64}$");
    }
    String eventType = requested.startsWith("manual.") ? requested : "manual." + requested;
    return auditService.append(
        request.systemId(), eventType, request.resourceType(), request.resourceId(),
        request.payload() == null ? Map.of() : request.payload(), "manual");
  }
}
