package os.assurance.eu.api.assessment;

import os.assurance.eu.api.billing.EntitlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/systems/{systemId}/assessment")
public class AssessmentController {
  private final EntitlementService entitlements;
  private final AssessmentService assessment;
  private final TenantAuthorizationService authorizationService;

  public AssessmentController(AssessmentService assessment, TenantAuthorizationService authorizationService,
      EntitlementService entitlements) {
    this.entitlements = entitlements;
    this.assessment = assessment;
    this.authorizationService = authorizationService;
  }

  @GetMapping
  public AssessmentService.AssessmentResponse get(@PathVariable UUID systemId) {
    requireRead();
    return assessment.assess(systemId);
  }

  @PutMapping("/{proposalId}")
  public AssessmentService.AssessmentItem setApplicability(
      @PathVariable UUID systemId,
      @PathVariable UUID proposalId,
      @Valid @RequestBody ApplicabilityRequest request) {
    requireWriter();
    entitlements.requireSystemWritable(systemId);
    return assessment.setApplicability(systemId, proposalId, request.applicability());
  }

  @PostMapping("/exceptions")
  @ResponseStatus(HttpStatus.CREATED)
  public AssessmentService.AssessmentItem recordException(
      @PathVariable UUID systemId,
      @Valid @RequestBody ExceptionRequest request) {
    requireWriter();
    entitlements.requireSystemWritable(systemId);
    return assessment.recordException(systemId, request.proposalId(), request.rationale(), request.expiresOn());
  }

  @PostMapping("/{proposalId}/satisfied")
  public AssessmentService.AssessmentItem satisfied(
      @PathVariable UUID systemId,
      @PathVariable UUID proposalId) {
    requireWriter();
    entitlements.requireSystemWritable(systemId);
    return assessment.refuseSatisfied(systemId, proposalId);
  }

  private void requireRead() {
    authorizationService.requireAnyRole(
        UserRole.ADMIN,
        UserRole.AI_ENGINEERING_LEAD,
        UserRole.COMPLIANCE_OFFICER,
        UserRole.LEGAL_COUNSEL,
        UserRole.AUDITOR);
  }

  private void requireWriter() {
    authorizationService.requireAnyRole(UserRole.ADMIN, UserRole.COMPLIANCE_OFFICER);
  }

  public record ApplicabilityRequest(@NotBlank String applicability) {
  }

  public record ExceptionRequest(
      @NotNull UUID proposalId,
      @NotBlank @Size(max = 2048) String rationale,
      @NotNull LocalDate expiresOn) {
  }
}
