package os.assurance.eu.api.framework;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.billing.Feature;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.UserRole;

@RestController
@RequestMapping("/api/v1/systems/{systemId}")
public class GrcExportController {
  private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

  private final GrcExportService exports;
  private final EntitlementService entitlements;
  private final AiSystemRepository systems;
  private final TenantAuthorizationService authorization;

  public GrcExportController(GrcExportService exports, EntitlementService entitlements, AiSystemRepository systems,
      TenantAuthorizationService authorization) {
    this.exports = exports;
    this.entitlements = entitlements;
    this.systems = systems;
    this.authorization = authorization;
  }

  /** Same roles as the evidence pack: the ZIP carries a freshly signed pack. */
  @GetMapping("/grc-export")
  public ResponseEntity<byte[]> export(@PathVariable UUID systemId) {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER,
        UserRole.LEGAL_COUNSEL, UserRole.AUDITOR);
    entitlements.requireFeature(Feature.INTEGRATIONS);
    systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    GrcExportService.Export export = exports.build(systemId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
        .header("X-Content-Sha256", export.contentSha256())
        .contentType(ZIP)
        .body(export.zip());
  }
}
