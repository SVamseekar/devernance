package os.assurance.eu.api.regmonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class RegMonitorIngestionServiceTest {
  private RegSourceJpaRepository sources;
  private RegItemJpaRepository items;
  private RegImpactHintJpaRepository hints;
  private AuditService auditService;
  private PlatformTransactionManager transactionManager;
  private RegMonitorIngestionService service;
  private final Instant now = Instant.parse("2026-07-20T12:00:00Z");

  @BeforeEach
  void setUp() {
    sources = mock(RegSourceJpaRepository.class);
    items = mock(RegItemJpaRepository.class);
    hints = mock(RegImpactHintJpaRepository.class);
    auditService = mock(AuditService.class);
    transactionManager = mock(PlatformTransactionManager.class);
    RegMonitorProperties props = new RegMonitorProperties();
    props.setBootstrapFixtures(true);
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    service = new RegMonitorIngestionService(
        sources,
        items,
        hints,
        new RegFeedParser(new ObjectMapper()),
        new SsrfSafeHttpFetcher(props),
        new RegImpactMapper(),
        props,
        auditService,
        clock,
        transactionManager);
  }

  @Test
  void bootstrapFixtureIngestsAndDedupes() throws Exception {
    UUID sourceId = UUID.fromString("a1600001-0000-4000-8000-000000000001");
    RegSourceEntity source = new RegSourceEntity(new RegSource(
        sourceId,
        "CURATED_BOOTSTRAP",
        "Curated bootstrap",
        "classpath:reg-monitor/bootstrap-feed.json",
        FeedType.STATIC_FIXTURE,
        3600,
        true,
        null,
        "fixture",
        now));

    when(sources.findByCode("CURATED_BOOTSTRAP")).thenReturn(Optional.of(source));
    when(items.existsByContentHash(anyString())).thenReturn(false);
    when(items.existsBySourceIdAndExternalId(eq(sourceId), anyString())).thenReturn(false);
    when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(hints.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sources.save(any())).thenAnswer(inv -> inv.getArgument(0));

    int first = service.ensureBootstrapFixtures();
    assertThat(first).isGreaterThanOrEqualTo(4);
    verify(auditService, times(first)).append(
        eq(null),
        eq("reg_item.ingested"),
        eq("reg_item"),
        anyString(),
        any());

    // Second pass: all content hashes exist → zero new
    when(items.existsByContentHash(anyString())).thenReturn(true);
    int second = service.ensureBootstrapFixtures();
    assertThat(second).isZero();
  }

  @Test
  void auditPayloadNeverClaimsAutoMutation() throws Exception {
    UUID sourceId = UUID.fromString("a1600001-0000-4000-8000-000000000001");
    RegSourceEntity source = new RegSourceEntity(new RegSource(
        sourceId,
        "CURATED_BOOTSTRAP",
        "Curated bootstrap",
        "classpath:reg-monitor/bootstrap-feed.json",
        FeedType.STATIC_FIXTURE,
        3600,
        true,
        null,
        "fixture",
        now));
    when(sources.findByCode("CURATED_BOOTSTRAP")).thenReturn(Optional.of(source));
    when(items.existsByContentHash(anyString())).thenReturn(false);
    when(items.existsBySourceIdAndExternalId(eq(sourceId), anyString())).thenReturn(false);
    when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(hints.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sources.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.ensureBootstrapFixtures();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(auditService, times(5)).append(
        eq(null),
        eq("reg_item.ingested"),
        eq("reg_item"),
        anyString(),
        payloadCaptor.capture());

    for (Map<String, Object> payload : payloadCaptor.getAllValues()) {
      assertThat(payload.get("autoMutatesRiskOrControls")).isEqualTo(false);
    }
  }

  @Test
  void skippedWhenNotDue() {
    UUID sourceId = UUID.randomUUID();
    RegSourceEntity source = new RegSourceEntity(new RegSource(
        sourceId,
        "OJ_RSS",
        "OJ",
        "https://example.invalid/rss",
        FeedType.RSS,
        3600,
        true,
        now.minusSeconds(10),
        null,
        now));
    when(sources.findAllByEnabledTrueOrderByCreatedAtAsc()).thenReturn(List.of(source));

    int n = service.pollDueSources();
    assertThat(n).isZero();
    verify(items, never()).save(any());
  }

  @Test
  void dueSourceIsStoredInItsOwnTransactionAndMarkedPolled() {
    UUID sourceId = UUID.randomUUID();
    RegSourceEntity source = new RegSourceEntity(new RegSource(
        sourceId,
        "OJ_RSS",
        "OJ",
        "https://example.invalid/rss",
        FeedType.RSS,
        3600,
        true,
        null,
        null,
        now));
    when(sources.findAllByEnabledTrueOrderByCreatedAtAsc()).thenReturn(List.of(source));
    when(items.existsByContentHash(anyString())).thenReturn(false);
    when(items.existsBySourceIdAndExternalId(eq(sourceId), anyString())).thenReturn(false);
    when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(hints.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    int n = service.pollDueSources();

    // The unreachable feed falls back to the curated fixture; its items commit in one transaction.
    assertThat(n).isGreaterThanOrEqualTo(4);
    verify(transactionManager, times(1)).getTransaction(any());
    verify(transactionManager, times(1)).commit(any());
    verify(sources).save(source);
    assertThat(source.lastPolledAt()).isEqualTo(now);
  }
}
