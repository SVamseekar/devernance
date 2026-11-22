package os.assurance.eu.api.control;

import os.assurance.eu.api.billing.EntitlementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.UserRole;

@RestController
@RequestMapping("/api/v1")
public class ControlController {
  private final EntitlementService entitlements;
  private final ControlService controlService;
  private final TenantAuthorizationService authorizationService;

  public ControlController(ControlService controlService, TenantAuthorizationService authorizationService,
      EntitlementService entitlements) {
    this.entitlements = entitlements;
    this.controlService = controlService;
    this.authorizationService = authorizationService;
  }

  @GetMapping("/controls")
  public List<Control> listControls() {
    return controlService.listCatalog();
  }

  @GetMapping("/systems/{systemId}/controls")
  public List<SystemControl> listSystemControls(@PathVariable UUID systemId) {
    return controlService.listForSystem(systemId);
  }

  @PutMapping("/systems/{systemId}/controls/{controlId}")
  public SystemControl updateSystemControl(
      @PathVariable UUID systemId,
      @PathVariable UUID controlId,
      @Valid @RequestBody UpdateSystemControlRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.COMPLIANCE_OFFICER, UserRole.AI_ENGINEERING_LEAD);
    entitlements.requireSystemWritable(systemId);
    return controlService.updateSystemControl(systemId, controlId, request, authorizationService.currentRole());
  }
}
