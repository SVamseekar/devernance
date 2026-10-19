package os.assurance.eu.api.regmonitor;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls enabled sources, fetches SSRF-safely (or loads fixtures), dedupes on content_hash,
 * writes impact hints. Never mutates risk class or control status.
 */
@Service
public class RegMonitorIngestionService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegMonitorIngestionService.class);

  private final RegSourceJpaRepository sources;
  private final RegItemJpaRepository items;
  private final RegImpactHintJpaRepository hints;
  private final RegFeedParser feedParser;
  private final SsrfSafeHttpFetcher httpFetcher;
  private final RegImpactMapper impactMapper;
  private final RegMonitorProperties properties;
  private final AuditService auditService;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public RegMonitorIngestionService(
      RegSourceJpaRepository sources,
      RegItemJpaRepository items,
      RegImpactHintJpaRepository hints,
      RegFeedParser feedParser,
      SsrfSafeHttpFetcher httpFetcher,
      RegImpactMapper impactMapper,
      RegMonitorProperties properties,
      AuditService auditService,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.sources = sources;
    this.items = items;
    this.hints = hints;
    this.feedParser = feedParser;
    this.httpFetcher = httpFetcher;
    this.impactMapper = impactMapper;
    this.properties = properties;
    this.auditService = auditService;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * Poll all enabled sources that are due. Safe to call from scheduler. Each feed is fetched outside
   * any transaction, so a slow source never holds a database connection, and each source's items are
   * stored in a transaction of their own, so one failing source does not undo the others.
   *
   * @return number of newly ingested items
   */
  public int pollDueSources() {
    Instant now = clock.instant();
    int ingested = 0;
    for (RegSourceEntity source : sources.findAllByEnabledTrueOrderByCreatedAtAsc()) {
      if (!isDue(source, now)) {
        continue;
      }
      try {
        List<ParsedFeedItem> parsed = loadItems(source);
        Integer stored = transaction.execute(status -> storeItems(source, parsed, now));
        ingested += stored == null ? 0 : stored;
      } catch (Exception ex) {
        LOGGER.warn("Reg-monitor poll failed for source {}: {}", source.code(), ex.getMessage());
      } finally {
        source.markPolled(now);
        sources.save(source);
      }
    }
    return ingested;
  }

  /**
   * Force bootstrap fixture ingestion (e.g. first boot when network blocked).
   */
  @Transactional
  public int ensureBootstrapFixtures() {
    if (!properties.isBootstrapFixtures()) {
      return 0;
    }
    return sources.findByCode("CURATED_BOOTSTRAP")
        .map(source -> {
          try {
            Instant now = clock.instant();
            int n = ingestSource(source, now);
            source.markPolled(now);
            sources.save(source);
            return n;
          } catch (Exception ex) {
            LOGGER.warn("Bootstrap fixture ingest failed: {}", ex.getMessage());
            return 0;
          }
        })
        .orElse(0);
  }

  int ingestSource(RegSourceEntity source, Instant now) throws IOException {
    return storeItems(source, loadItems(source), now);
  }

  private int storeItems(RegSourceEntity source, List<ParsedFeedItem> parsed, Instant now) {
    int created = 0;
    for (ParsedFeedItem item : parsed) {
      if (ingestItem(source, item, now)) {
        created++;
      }
    }
    return created;
  }

  private List<ParsedFeedItem> loadItems(RegSourceEntity source) throws IOException {
    if (source.feedType() == FeedType.STATIC_FIXTURE) {
      return feedParser.loadClasspathFixture(source.url());
    }
    if (source.feedType() == FeedType.RSS) {
      try {
        String body = httpFetcher.fetchHttps(source.url());
        List<ParsedFeedItem> items = feedParser.parseRss(body);
        if (!items.isEmpty()) {
          return items;
        }
      } catch (Exception ex) {
        LOGGER.info(
            "RSS fetch failed for {} ({}), falling back to bootstrap if available: {}",
            source.code(),
            source.url(),
            ex.getMessage());
      }
      if (properties.isBootstrapFixtures()) {
        return feedParser.loadClasspathFixture("classpath:reg-monitor/bootstrap-feed.json");
      }
      return List.of();
    }
    // HTML_LIST: v1 does not scrape HTML. Prefer bootstrap when enabled.
    if (properties.isBootstrapFixtures()) {
      LOGGER.info(
          "HTML_LIST source {} is assistive-only in v1; using curated bootstrap fixture",
          source.code());
      return feedParser.loadClasspathFixture("classpath:reg-monitor/bootstrap-feed.json");
    }
    return List.of();
  }

  private boolean ingestItem(RegSourceEntity source, ParsedFeedItem parsed, Instant now) {
    String contentHash = RegContentHasher.hash(parsed);
    if (items.existsByContentHash(contentHash)
        || items.existsBySourceIdAndExternalId(source.id(), parsed.externalId())) {
      return false;
    }

    UUID itemId = UUID.randomUUID();
    RegItemEntity entity = new RegItemEntity(
        itemId,
        source.id(),
        parsed.externalId(),
        parsed.title(),
        parsed.summary(),
        parsed.publishedAt(),
        parsed.url(),
        contentHash,
        now);
    items.save(entity);

    List<RegImpactHint> mapped = impactMapper.mapHints(itemId, parsed.title(), parsed.summary(), now);
    List<RegImpactHintEntity> hintEntities = new ArrayList<>();
    for (RegImpactHint hint : mapped) {
      // Force UNCERTAIN in ingestion path (defense in depth).
      RegImpactHint uncertain = new RegImpactHint(
          hint.id(),
          hint.regItemId(),
          hint.controlCode(),
          hint.obligationCode(),
          ImpactLevel.UNCERTAIN,
          hint.impactNote(),
          hint.createdAt());
      hintEntities.add(new RegImpactHintEntity(uncertain));
    }
    hints.saveAll(hintEntities);

    auditService.append(
        null,
        "reg_item.ingested",
        "reg_item",
        itemId.toString(),
        Map.of(
            "sourceCode", source.code(),
            "externalId", parsed.externalId(),
            "contentHash", contentHash,
            "title", truncate(parsed.title(), 200),
            "impactHintCount", hintEntities.size(),
            "autoMutatesRiskOrControls", false));
    return true;
  }

  private static boolean isDue(RegSourceEntity source, Instant now) {
    Instant last = source.lastPolledAt();
    if (last == null) {
      return true;
    }
    long interval = Math.max(60, source.pollIntervalSeconds());
    return last.plusSeconds(interval).isBefore(now) || last.plusSeconds(interval).equals(now);
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
