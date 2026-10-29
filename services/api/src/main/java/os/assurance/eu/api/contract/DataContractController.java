package os.assurance.eu.api.contract;

import os.assurance.eu.api.billing.EntitlementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;

@RestController
@RequestMapping("/api/v1/data-contracts")
public class DataContractController {
  private final EntitlementService entitlements;
  private final DataContractService service;
  private final TenantAuthorizationService authorizationService;
  private final TenantContext tenantContext;

  public DataContractController(DataContractService service, TenantAuthorizationService authorizationService,
      EntitlementService entitlements, TenantContext tenantContext) {
    this.entitlements = entitlements;
    this.service = service;
    this.authorizationService = authorizationService;
    this.tenantContext = tenantContext;
  }

  @GetMapping
  public List<DataContract> listContracts(@RequestParam(required = false) UUID systemId) {
    return service.listContracts(systemId);
  }

  @GetMapping("/{contractId}")
  public DataContract getContract(@PathVariable UUID contractId) {
    return service.getContract(contractId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DataContract createContract(@Valid @RequestBody CreateDataContractRequest request) {
    requireContractMutator();
    entitlements.requireSystemWritable(request.systemId());
    return service.createContract(request);
  }

  @PatchMapping("/{contractId}")
  public DataContract updateContract(
      @PathVariable UUID contractId,
      @Valid @RequestBody UpdateDataContractRequest request) {
    requireContractMutator();
    entitlements.requireSystemWritable(service.getContract(contractId).systemId());
    return service.updateContract(contractId, request);
  }

  @GetMapping("/{contractId}/drift-events")
  public List<DriftEvent> listDriftEvents(@PathVariable UUID contractId) {
    return service.listDriftEvents(contractId);
  }

  @PostMapping("/{contractId}/drift-events")
  @ResponseStatus(HttpStatus.CREATED)
  public DriftEvent createDriftEvent(
      @PathVariable UUID contractId,
      @Valid @RequestBody DriftEventRequest request) {
    requireContractMutator();
    entitlements.requireSystemWritable(service.getContract(contractId).systemId());
    return service.createDriftEvent(contractId, request);
  }

  @PatchMapping("/{contractId}/drift-events/{eventId}")
  public DriftEvent updateDriftEvent(
      @PathVariable UUID contractId,
      @PathVariable UUID eventId,
      @Valid @RequestBody UpdateDriftEventRequest request) {
    requireContractMutator();
    entitlements.requireSystemWritable(service.getContract(contractId).systemId());
    return service.updateDriftEvent(
        contractId, eventId, request, authorizationService.currentRole(), tenantContext.actorId());
  }

  private void requireContractMutator() {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);
  }
}
