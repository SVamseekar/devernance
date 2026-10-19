package os.assurance.eu.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.observability.AssuranceMetrics;
import os.assurance.eu.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditChainServiceTest {
  private AuditEventJpaRepository repository;
  private AuditChainHeadJpaRepository heads;
  private AuditChainHeads headInit;
  private AuditChainHeadEntity head;
  private TenantContext tenantContext;
  private AuditChainHasher hasher;
  private AuditService service;

  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000101");

  @BeforeEach
  void setUp() {
    repository = mock(AuditEventJpaRepository.class);
    tenantContext = mock(TenantContext.class);
    when(tenantContext.tenantId()).thenReturn(TENANT);
    when(tenantContext.actorId()).thenReturn(ACTOR);
    hasher = new AuditChainHasher("test-audit-chain-secret");
    heads = mock(AuditChainHeadJpaRepository.class);
    headInit = mock(AuditChainHeads.class);
    head = new AuditChainHeadEntity(TENANT, null, Instant.now());
    when(heads.findForUpdate(TENANT)).thenReturn(Optional.of(head));
    service = new AuditService(repository, heads, headInit, tenantContext, hasher, mock(AssuranceMetrics.class), 7);
  }

  @Test
  void appendChainsHashesAndSetsSevenYearRetention() {
    when(repository.findLatestByTenantId(TENANT)).thenReturn(Optional.empty());
    ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

    AuditEvent first = service.append(null, "test.a", "x", "1", Map.of("k", "v"));
    assertThat(first.prevEventHash()).isNull();
    assertThat(first.eventHash()).isNotBlank();
    assertThat(first.retainUntil()).isAfter(Instant.now().plusSeconds(3600L * 24 * 365 * 6));

    AuditEvent second = service.append(null, "test.b", "x", "2", Map.of("k", "v2"));
    assertThat(second.prevEventHash()).isEqualTo(first.eventHash());
    assertThat(second.eventHash()).isNotEqualTo(first.eventHash());
  }

  @Test
  void verifyDetectsTamperedPayload() {
    AuditChainHasher local = new AuditChainHasher("test-audit-chain-secret");
    UUID id1 = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    String h1 = local.hash(TENANT, id1, null, ACTOR, "a", "r", "1", Map.of("x", 1), t1);
    AuditEventEntity e1 = new AuditEventEntity(
        id1, TENANT, null, ACTOR, "a", "r", "1", Map.of("x", 1), t1, null, h1,
        t1.plusSeconds(3600L * 24 * 365 * 7));

    UUID id2 = UUID.randomUUID();
    Instant t2 = Instant.parse("2026-01-01T00:00:01Z");
    String h2 = local.hash(TENANT, id2, h1, ACTOR, "b", "r", "2", Map.of("x", 2), t2);
    // tamper payload while keeping stored hash
    AuditEventEntity e2 = new AuditEventEntity(
        id2, TENANT, null, ACTOR, "b", "r", "2", Map.of("x", 999), t2, h1, h2,
        t2.plusSeconds(3600L * 24 * 365 * 7));

    chainIs(e1, e2);
    AuditChainVerifyResponse result = service.verifyChain();
    assertThat(result.valid()).isFalse();
    assertThat(result.firstBreakId()).isEqualTo(id2);
  }

  @Test
  void firstAppendAfterMigrationLinksToLatestLegacyEvent() {
    AuditEventEntity legacy = new AuditEventEntity(
        UUID.randomUUID(), TENANT, null, ACTOR, "legacy", "r", "1", Map.of(), Instant.parse("2026-01-01T00:00:00Z"),
        null, "a".repeat(64), Instant.parse("2033-01-01T00:00:00Z"));
    when(repository.findLatestByTenantId(TENANT)).thenReturn(Optional.of(legacy));
    when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
    AuditEvent next = service.append(null, "x.y", "r", "2", Map.of());
    assertThat(next.prevEventHash()).isEqualTo("a".repeat(64));
    assertThat(head.headHash()).isEqualTo(next.eventHash());
  }

  @Test
  void verifyReportsForkWhenTwoEventsShareAPredecessor() {
    AuditChainHasher local = new AuditChainHasher("test-audit-chain-secret");
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    UUID g = UUID.randomUUID();
    String hg = local.hash(TENANT, g, null, ACTOR, "a", "r", "1", Map.of(), t);
    UUID b1 = UUID.randomUUID();
    String h1 = local.hash(TENANT, b1, hg, ACTOR, "b", "r", "2", Map.of(), t.plusSeconds(1));
    UUID b2 = UUID.randomUUID();
    String h2 = local.hash(TENANT, b2, hg, ACTOR, "c", "r", "3", Map.of(), t.plusSeconds(1));
    Instant keep = t.plusSeconds(3600L * 24 * 365 * 7);
    chainIs(
        new AuditEventEntity(g, TENANT, null, ACTOR, "a", "r", "1", Map.of(), t, null, hg, keep),
        new AuditEventEntity(b1, TENANT, null, ACTOR, "b", "r", "2", Map.of(), t.plusSeconds(1), hg, h1, keep),
        new AuditEventEntity(b2, TENANT, null, ACTOR, "c", "r", "3", Map.of(), t.plusSeconds(1), hg, h2, keep));
    AuditChainVerifyResponse result = service.verifyChain();
    assertThat(result.valid()).isFalse();
  }

  @Test
  void verifiesALongChainInBatches() {
    AuditChainHasher local = new AuditChainHasher("test-audit-chain-secret");
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    Instant keep = t.plusSeconds(3600L * 24 * 365 * 7);
    int count = AuditService.VERIFY_BATCH * 2 + 7;
    AuditEventEntity[] events = new AuditEventEntity[count];
    String prev = null;
    for (int i = 0; i < count; i++) {
      UUID id = UUID.randomUUID();
      Instant at = t.plusSeconds(i);
      String hash = local.hash(TENANT, id, prev, ACTOR, "e", "r", String.valueOf(i), Map.of("i", i), at);
      events[i] = new AuditEventEntity(id, TENANT, null, ACTOR, "e", "r", String.valueOf(i), Map.of("i", i), at, prev, hash, keep);
      prev = hash;
    }
    chainIs(events);
    AuditChainVerifyResponse result = service.verifyChain();
    assertThat(result.valid()).isTrue();
    assertThat(result.checkedCount()).isEqualTo(count);
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(3)).findChainRows(any());
  }

  /** Serves the given events through the two projections verifyChain reads. */
  private void chainIs(AuditEventEntity... events) {
    List<AuditEventEntity> all = List.of(events);
    when(repository.findChainLinks(TENANT)).thenReturn(all.stream().map(AuditChainServiceTest::link).toList());
    when(repository.findChainRows(any())).thenAnswer(invocation -> {
      java.util.Collection<UUID> ids = invocation.getArgument(0);
      return all.stream().filter(e -> ids.contains(e.id())).map(AuditChainServiceTest::row).toList();
    });
  }

  private static AuditEventJpaRepository.ChainLink link(AuditEventEntity e) {
    return new AuditEventJpaRepository.ChainLink() {
      @Override public UUID getId() { return e.id(); }
      @Override public String getPrevEventHash() { return e.prevEventHash(); }
      @Override public String getEventHash() { return e.eventHash(); }
    };
  }

  private static AuditEventJpaRepository.ChainRow row(AuditEventEntity e) {
    return new AuditEventJpaRepository.ChainRow() {
      @Override public UUID getId() { return e.id(); }
      @Override public UUID getTenantId() { return e.tenantId(); }
      @Override public String getPrevEventHash() { return e.prevEventHash(); }
      @Override public UUID getActorId() { return e.actorId(); }
      @Override public String getEventType() { return e.eventType(); }
      @Override public String getResourceType() { return e.resourceType(); }
      @Override public String getResourceId() { return e.resourceId(); }
      @Override public Map<String, Object> getPayload() { return e.payload(); }
      @Override public Instant getCreatedAt() { return e.createdAt(); }
      @Override public String getEventHash() { return e.eventHash(); }
    };
  }
}
