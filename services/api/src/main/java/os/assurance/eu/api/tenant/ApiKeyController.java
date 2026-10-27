package os.assurance.eu.api.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {
  private final ApiKeyService service;
  private final TenantAuthorizationService authorization;

  public ApiKeyController(ApiKeyService service, TenantAuthorizationService authorization) {
    this.service = service;
    this.authorization = authorization;
  }

  @GetMapping
  public List<ApiKeyService.View> list() {
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD);
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiKeyService.Created create(HttpServletRequest request, @RequestBody(required = false) CreateBody body) {
    SessionOnly.require(request);
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD);
    return service.create(body == null ? null : body.name(), body == null ? null : body.scope());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(HttpServletRequest request, @PathVariable UUID id) {
    SessionOnly.require(request);
    authorization.requireAnyRole(UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD);
    service.revoke(id);
  }

  public record CreateBody(String name, ApiKeyScope scope) {}
}
