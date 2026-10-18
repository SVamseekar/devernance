package os.assurance.eu.api.account;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import os.assurance.eu.api.tenant.SessionOnly;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

/** Self-service data export and deletion for workspace admins, from a signed-in session only. */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
  private final WorkspaceExportService export;
  private final WorkspaceDeletionService deletion;
  private final TenantAuthorizationService authorization;
  private final TenantContext tenantContext;
  private final DpaAcceptanceService dpa;

  public AccountController(WorkspaceExportService export, WorkspaceDeletionService deletion,
      TenantAuthorizationService authorization, TenantContext tenantContext, DpaAcceptanceService dpa) {
    this.export = export;
    this.deletion = deletion;
    this.authorization = authorization;
    this.tenantContext = tenantContext;
    this.dpa = dpa;
  }

  /** Which DPA version this workspace accepted, if any. Any member may read it. */
  @GetMapping("/dpa-acceptance")
  public DpaAcceptanceService.DpaAcceptance dpaAcceptance() {
    return dpa.current(tenantContext.tenantId());
  }

  /** An admin accepts the DPA for the organisation, from a signed-in session. Audited as account.dpa_accepted. */
  @PostMapping("/dpa-acceptance")
  public DpaAcceptanceService.DpaAcceptance acceptDpa(HttpServletRequest request, @RequestBody DpaBody body) {
    SessionOnly.require(request);
    authorization.requireAnyRole(UserRole.ADMIN);
    return dpa.accept(tenantContext.tenantId(), body == null ? null : body.version());
  }

  public record DpaBody(String version) {}

  @GetMapping(value = "/export", produces = "application/zip")
  public ResponseEntity<StreamingResponseBody> export(HttpServletRequest request) {
    SessionOnly.require(request);
    authorization.requireAnyRole(UserRole.ADMIN);
    UUID tenantId = tenantContext.tenantId();
    export.recordExport(tenantId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"devernance-export.zip\"")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(out -> export.writeTo(tenantId, out));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, String> delete(HttpServletRequest request, @RequestBody(required = false) DeleteBody body) {
    SessionOnly.require(request);
    authorization.requireAnyRole(UserRole.ADMIN);
    deletion.delete(body == null ? null : body.confirmOrganisationName());
    return Map.of("status", "deletion_scheduled");
  }

  public record DeleteBody(String confirmOrganisationName) {}
}
