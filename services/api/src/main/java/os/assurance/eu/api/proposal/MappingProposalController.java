package os.assurance.eu.api.proposal;

import os.assurance.eu.api.billing.EntitlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/proposals")
public class MappingProposalController {
  private final EntitlementService entitlements;
  private final MappingProposalService proposals;
  private final TenantAuthorizationService authorizationService;

  public MappingProposalController(
      MappingProposalService proposals,
      TenantAuthorizationService authorizationService,
      EntitlementService entitlements) {
    this.entitlements = entitlements;
    this.proposals = proposals;
    this.authorizationService = authorizationService;
  }

  @GetMapping
  public MappingProposalService.ProposalListResponse list(@PathVariable UUID systemId) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN,
        UserRole.AI_ENGINEERING_LEAD,
        UserRole.COMPLIANCE_OFFICER,
        UserRole.LEGAL_COUNSEL,
        UserRole.AUDITOR);
    return proposals.list(systemId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MappingProposalService.ProposalView create(
      @PathVariable UUID systemId,
      @Valid @RequestBody CreateMappingProposalRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN,
        UserRole.COMPLIANCE_OFFICER,
        UserRole.AI_ENGINEERING_LEAD);
    entitlements.requireSystemWritable(systemId);
    return proposals.create(systemId, request);
  }

  @PostMapping("/map")
  @ResponseStatus(HttpStatus.CREATED)
  public List<MappingProposalService.ProposalView> map(
      @PathVariable UUID systemId,
      @Valid @RequestBody MapDocumentsRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN,
        UserRole.COMPLIANCE_OFFICER,
        UserRole.AI_ENGINEERING_LEAD);
    entitlements.requireSystemWritable(systemId);
    return proposals.mapDocuments(
        systemId,
        request.documents().stream()
            .map(document -> new MappingProposalService.MapDocumentRequest(document.title(), document.text()))
            .toList());
  }

  @PostMapping("/{proposalId}/accept")
  public MappingProposalService.ProposalView accept(
      @PathVariable UUID systemId,
      @PathVariable UUID proposalId) {
    requireDecisionRole();
    entitlements.requireSystemWritable(systemId);
    return proposals.accept(systemId, proposalId);
  }

  @PutMapping("/{proposalId}/mode")
  public MappingProposalService.ProposalView setMode(
      @PathVariable UUID systemId,
      @PathVariable UUID proposalId,
      @Valid @RequestBody SetModeRequest request) {
    requireDecisionRole();
    entitlements.requireSystemWritable(systemId);
    return proposals.setMode(systemId, proposalId, request.mode());
  }

  @PostMapping("/{proposalId}/reject")
  public MappingProposalService.ProposalView reject(
      @PathVariable UUID systemId,
      @PathVariable UUID proposalId) {
    requireDecisionRole();
    entitlements.requireSystemWritable(systemId);
    return proposals.reject(systemId, proposalId);
  }

  private void requireDecisionRole() {
    authorizationService.requireAnyRole(UserRole.ADMIN, UserRole.COMPLIANCE_OFFICER);
  }

  public record SetModeRequest(@NotBlank String mode) {
  }

  /** Bounded so one request cannot queue an unbounded number of model calls. */
  public record MapDocumentsRequest(@NotEmpty @Size(max = 20) @Valid List<MapDocument> documents) {
    public record MapDocument(@NotBlank @Size(max = 300) String title, @NotBlank @Size(max = 100_000) String text) {
    }
  }
}
