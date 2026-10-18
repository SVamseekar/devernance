package os.assurance.eu.api.tenant;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.audit.AuditService;

@Service
public class ApiKeyService {
  public record Created(UUID id, String name, String prefix, String key) {}

  public record View(UUID id, String name, String prefix, Instant createdAt, Instant lastUsedAt, UUID createdBy) {}

  private final SecureRandom random = new SecureRandom();
  private final ApiKeyJpaRepository keys;
  private final TenantContext tenantContext;
  private final AuditService audit;
  private final Clock clock;

  public ApiKeyService(ApiKeyJpaRepository keys, TenantContext tenantContext, AuditService audit, Clock clock) {
    this.keys = keys;
    this.tenantContext = tenantContext;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Created create(String name) {
    String cleanName = name == null || name.isBlank() ? "API key" : name.trim();
    if (cleanName.length() > 80) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be at most 80 characters");
    }
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = "dvn_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String prefix = raw.substring(0, 12);
    ApiKeyEntity saved = keys.save(new ApiKeyEntity(UUID.randomUUID(), ApiKeyHasher.sha256Hex(raw),
        tenantContext.tenantId(), tenantContext.actorId(), clock.instant(), cleanName, prefix));
    audit.append(null, "api_key.created", "api_key", saved.id().toString(), Map.of("name", cleanName, "prefix", prefix));
    return new Created(saved.id(), cleanName, prefix, raw);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    return keys.findAllByTenantIdAndRevokedAtIsNullOrderByCreatedAtDesc(tenantContext.tenantId()).stream()
        .map(k -> new View(k.id(), k.name(), k.prefix(), k.createdAt(), k.lastUsedAt(), k.userId()))
        .toList();
  }

  @Transactional
  public void revoke(UUID id) {
    ApiKeyEntity key = keys.findByIdAndTenantId(id, tenantContext.tenantId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
    key.revoke(clock.instant());
    keys.save(key);
    audit.append(null, "api_key.revoked", "api_key", id.toString(), Map.of("prefix", String.valueOf(key.prefix())));
  }
}
