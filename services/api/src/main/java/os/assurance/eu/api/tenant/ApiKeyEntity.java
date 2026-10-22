package os.assurance.eu.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {
    @Id private UUID id;
    @Column(name = "key_hash", nullable = false, unique = true) private String keyHash;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "name") private String name;
    @Column(name = "prefix") private String prefix;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32) private ApiKeyScope scope = ApiKeyScope.FULL;

    protected ApiKeyEntity() {}

    public ApiKeyEntity(UUID id, String keyHash, UUID tenantId, UUID userId, Instant createdAt) {
        this.id = id;
        this.keyHash = keyHash;
        this.tenantId = tenantId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public ApiKeyEntity(UUID id, String keyHash, UUID tenantId, UUID userId, Instant createdAt, String name, String prefix) {
        this(id, keyHash, tenantId, userId, createdAt);
        this.name = name;
        this.prefix = prefix;
    }

    public ApiKeyEntity(UUID id, String keyHash, UUID tenantId, UUID userId, Instant createdAt, String name,
            String prefix, ApiKeyScope scope) {
        this(id, keyHash, tenantId, userId, createdAt, name, prefix);
        this.scope = scope;
    }

    public UUID id() { return id; }
    public String keyHash() { return keyHash; }
    public UUID tenantId() { return tenantId; }
    public UUID userId() { return userId; }
    public Instant createdAt() { return createdAt; }
    public String name() { return name; }
    public String prefix() { return prefix; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public boolean revoked() { return revokedAt != null; }
    public ApiKeyScope scope() { return scope == null ? ApiKeyScope.FULL : scope; }
    public void revoke(Instant at) { this.revokedAt = at; }
}
