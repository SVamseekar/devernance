package os.assurance.eu.api.audit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import os.assurance.eu.api.observability.AssuranceMetrics;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private final AuditEventJpaRepository repository;
  private final AuditChainHeadJpaRepository heads;
  private final AuditChainHeads headInit;
  private final TenantContext tenantContext;
  private final AuditChainHasher chainHasher;
  private final AssuranceMetrics assuranceMetrics;
  private final int retentionYears;
  static final int VERIFY_BATCH = 500;
  static final Duration RECENT_VERIFICATION = Duration.ofMinutes(5);
  private final Map<UUID, CachedVerification> recentVerifications = new ConcurrentHashMap<>();

  public AuditService(
      AuditEventJpaRepository repository,
      AuditChainHeadJpaRepository heads,
      AuditChainHeads headInit,
      TenantContext tenantContext,
      AuditChainHasher chainHasher,
      AssuranceMetrics assuranceMetrics,
      @Value("${assurance.audit.retention-years:7}") int retentionYears) {
    this.repository = repository;
    this.heads = heads;
    this.headInit = headInit;
    this.tenantContext = tenantContext;
    this.chainHasher = chainHasher;
    this.assuranceMetrics = assuranceMetrics;
    this.retentionYears = Math.max(1, retentionYears);
  }

  @Transactional
  public AuditEvent append(
      UUID systemId, String eventType, String resourceType, String resourceId, Map<String, Object> payload) {
    return append(systemId, eventType, resourceType, resourceId, payload, "system");
  }

  @Transactional
  public AuditEvent append(
      UUID systemId, String eventType, String resourceType, String resourceId,
      Map<String, Object> payload, String source) {
    UUID tenantId = tenantContext.tenantId();
    headInit.ensureExists(tenantId);
    AuditChainHeadEntity head = heads.findForUpdate(tenantId)
        .orElseThrow(() -> new IllegalStateException("Audit chain head missing for tenant " + tenantId));
    UUID id = UUID.randomUUID();
    // Truncate to millis so hash input matches JDBC/H2 timestamp round-trip precision.
    Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant retainUntil = createdAt.atZone(ZoneOffset.UTC).plusYears(retentionYears).toInstant();
    String prevHash = head.headHash() != null
        ? head.headHash()
        : repository.findLatestByTenantId(tenantId).map(AuditEventEntity::eventHash).orElse(null);
    String eventHash = chainHasher.hash(
        tenantId, id, prevHash, tenantContext.actorId(), eventType, resourceType, resourceId, payload, createdAt);
    AuditEventEntity event = new AuditEventEntity(
        id, tenantId, systemId, tenantContext.actorId(), eventType, resourceType, resourceId, payload,
        createdAt, prevHash, eventHash, retainUntil);
    event.markSource("manual".equals(source) ? "manual" : "system");
    AuditEvent saved = repository.save(event).toDomain();
    head.advance(eventHash, createdAt);
    heads.save(head);
    assuranceMetrics.auditAppend();
    return saved;
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> findBySystemId(UUID systemId) {
    return repository.findAllByTenantIdAndSystemIdOrderByCreatedAtDesc(
            tenantContext.tenantId(), systemId).stream()
        .map(AuditEventEntity::toDomain)
        .toList();
  }

  /**
   * Newest events first, at most {@code limit}, optionally only those created before {@code before} (the
   * {@code createdAt} of the last event already shown), for paging through a long trail.
   */
  @Transactional(readOnly = true)
  public List<AuditEvent> recent(UUID systemId, Instant before, int limit) {
    Instant cursor = before != null ? before : Instant.now().plus(1, ChronoUnit.DAYS);
    Limit max = Limit.of(limit);
    List<AuditEventEntity> rows = systemId == null
        ? repository.findByTenantIdAndCreatedAtBeforeOrderByCreatedAtDesc(tenantContext.tenantId(), cursor, max)
        : repository.findByTenantIdAndSystemIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            tenantContext.tenantId(), systemId, cursor, max);
    return rows.stream().map(AuditEventEntity::toDomain).toList();
  }

  /**
   * Tip of the tenant audit hash chain (latest eventHash), if any chained event exists.
   */
  @Transactional(readOnly = true)
  public java.util.Optional<String> chainHeadHash() {
    return repository.findLatestByTenantId(tenantContext.tenantId())
        .map(AuditEventEntity::eventHash)
        .filter(hash -> hash != null && !hash.isBlank());
  }

  /**
   * A verification no older than {@link #RECENT_VERIFICATION}, for views that show chain health as one signal
   * among many (certification readiness). The verify endpoints always check the chain afresh.
   */
  public AuditChainVerifyResponse recentVerification() {
    UUID tenantId = tenantContext.tenantId();
    Instant now = Instant.now();
    CachedVerification cached = recentVerifications.get(tenantId);
    if (cached != null && cached.checkedAt().plus(RECENT_VERIFICATION).isAfter(now)) {
      return cached.result();
    }
    AuditChainVerifyResponse result = verifyChain();
    if (recentVerifications.size() > 10_000) {
      recentVerifications.clear();
    }
    recentVerifications.put(tenantId, new CachedVerification(result, now));
    return result;
  }

  private record CachedVerification(AuditChainVerifyResponse result, Instant checkedAt) {}

  /**
   * Walks the whole chain from its hash links (no payloads), then checks each event's hash in batches of
   * {@link #VERIFY_BATCH}, so memory stays flat however long the trail grows.
   */
  public AuditChainVerifyResponse verifyChain() {
    List<AuditEventJpaRepository.ChainLink> chained = repository.findChainLinks(tenantContext.tenantId()).stream()
        .filter(e -> e.getEventHash() != null && !e.getEventHash().isBlank())
        .toList();
    if (chained.isEmpty()) {
      return new AuditChainVerifyResponse(true, 0, null);
    }
    Map<String, List<AuditEventJpaRepository.ChainLink>> byPrev = new java.util.HashMap<>();
    for (AuditEventJpaRepository.ChainLink e : chained) {
      byPrev.computeIfAbsent(e.getPrevEventHash() == null ? "" : e.getPrevEventHash(), k -> new ArrayList<>()).add(e);
    }
    for (List<AuditEventJpaRepository.ChainLink> siblings : byPrev.values()) {
      if (siblings.size() > 1) {
        return new AuditChainVerifyResponse(false, 0, siblings.get(1).getId());
      }
    }
    // genesis: the event whose predecessor is not itself a chained event
    java.util.Set<String> hashes = new java.util.HashSet<>();
    chained.forEach(e -> hashes.add(e.getEventHash()));
    AuditEventJpaRepository.ChainLink current = chained.stream()
        .filter(e -> e.getPrevEventHash() == null || !hashes.contains(e.getPrevEventHash()))
        .findFirst()
        .orElse(chained.get(0));
    List<UUID> order = new ArrayList<>();
    java.util.Set<UUID> visited = new java.util.HashSet<>();
    while (current != null && visited.add(current.getId())) {
      order.add(current.getId());
      List<AuditEventJpaRepository.ChainLink> next = byPrev.get(current.getEventHash());
      current = next == null ? null : next.get(0);
    }
    int checked = 0;
    for (int from = 0; from < order.size(); from += VERIFY_BATCH) {
      List<UUID> batch = order.subList(from, Math.min(order.size(), from + VERIFY_BATCH));
      Map<UUID, AuditEventJpaRepository.ChainRow> rows = new java.util.HashMap<>();
      repository.findChainRows(batch).forEach(row -> rows.put(row.getId(), row));
      for (UUID id : batch) {
        AuditEventJpaRepository.ChainRow row = rows.get(id);
        checked++;
        boolean ok = row != null && chainHasher.matches(
            row.getTenantId(), row.getId(), row.getPrevEventHash(), row.getActorId(), row.getEventType(),
            row.getResourceType(), row.getResourceId(), row.getPayload(), row.getCreatedAt(), row.getEventHash());
        if (!ok) {
          return new AuditChainVerifyResponse(false, checked, id);
        }
      }
    }
    if (checked != chained.size()) {
      UUID orphan = chained.stream().map(AuditEventJpaRepository.ChainLink::getId)
          .filter(id -> !visited.contains(id)).findFirst().orElse(null);
      return new AuditChainVerifyResponse(false, checked, orphan);
    }
    return new AuditChainVerifyResponse(true, checked, null);
  }
}
