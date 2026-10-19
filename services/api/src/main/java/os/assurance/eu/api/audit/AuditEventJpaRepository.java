package os.assurance.eu.api.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {
  List<AuditEventEntity> findAllByTenantIdAndSystemIdOrderByCreatedAtDesc(UUID tenantId, UUID systemId);

  List<AuditEventEntity> findByTenantIdAndCreatedAtBeforeOrderByCreatedAtDesc(
      UUID tenantId, Instant before, Limit limit);

  List<AuditEventEntity> findByTenantIdAndSystemIdAndCreatedAtBeforeOrderByCreatedAtDesc(
      UUID tenantId, UUID systemId, Instant before, Limit limit);

  /** The hash links of the whole chain, oldest first, without payloads. */
  @Query("select e.id as id, e.prevEventHash as prevEventHash, e.eventHash as eventHash "
      + "from AuditEventEntity e where e.tenantId = :tenantId order by e.createdAt asc, e.id asc")
  List<ChainLink> findChainLinks(@Param("tenantId") UUID tenantId);

  /** Everything the chain hash covers, for a batch of events; projections stay out of the persistence context. */
  @Query("select e.id as id, e.tenantId as tenantId, e.prevEventHash as prevEventHash, e.actorId as actorId, "
      + "e.eventType as eventType, e.resourceType as resourceType, e.resourceId as resourceId, "
      + "e.payload as payload, e.createdAt as createdAt, e.eventHash as eventHash "
      + "from AuditEventEntity e where e.id in :ids")
  List<ChainRow> findChainRows(@Param("ids") Collection<UUID> ids);

  @Query(value = """
      select * from audit_events
      where tenant_id = :tenantId
      order by created_at desc, id desc
      limit 1
      """, nativeQuery = true)
  Optional<AuditEventEntity> findLatestByTenantId(@Param("tenantId") UUID tenantId);

  interface ChainRow {
    UUID getId();

    UUID getTenantId();

    String getPrevEventHash();

    UUID getActorId();

    String getEventType();

    String getResourceType();

    String getResourceId();

    Map<String, Object> getPayload();

    Instant getCreatedAt();

    String getEventHash();
  }

  interface ChainLink {
    UUID getId();

    String getPrevEventHash();

    String getEventHash();
  }
}
