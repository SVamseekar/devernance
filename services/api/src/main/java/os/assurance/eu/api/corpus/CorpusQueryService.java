package os.assurance.eu.api.corpus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorpusQueryService {
  private final CorpusVersionJpaRepository versions;
  private final InstrumentJpaRepository instruments;
  private final ProvisionJpaRepository provisions;
  private final GuidanceDocJpaRepository guidanceDocs;
  private final CorpusRelationshipJpaRepository relationships;
  private final CorpusAsOf asOf;
  private record Cached(String versionHash, LocalDate asOf, CorpusView view, Map<String, ProvisionView> byKey) {}
  private volatile Cached cached;

  public CorpusQueryService(
      CorpusVersionJpaRepository versions,
      InstrumentJpaRepository instruments,
      ProvisionJpaRepository provisions,
      GuidanceDocJpaRepository guidanceDocs,
      CorpusRelationshipJpaRepository relationships,
      CorpusAsOf asOf) {
    this.asOf = asOf;
    this.versions = versions;
    this.instruments = instruments;
    this.provisions = provisions;
    this.guidanceDocs = guidanceDocs;
    this.relationships = relationships;
  }

  @Transactional(readOnly = true)
  public CorpusView current() {
    return cachedCurrent().view();
  }

  @Transactional(readOnly = true)
  public Optional<ProvisionView> provision(String provisionKey) {
    if (provisionKey == null || provisionKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(cachedCurrent().byKey().get(provisionKey));
  }

  private Cached cachedCurrent() {
    String hash = currentVersionHash();
    LocalDate today = asOf.today();
    Cached snapshot = cached;
    if (snapshot != null && snapshot.versionHash().equals(hash) && snapshot.asOf().equals(today)) {
      return snapshot;
    }
    CorpusView view = versions.findFirstByOrderByBuiltAtDesc()
        .map(version -> view(version, today))
        .orElseGet(() -> new CorpusView(null, List.of(), List.of(), List.of(), CorpusIngestService.ATTRIBUTION));
    Map<String, ProvisionView> byKey = new java.util.HashMap<>();
    for (ProvisionView p : view.provisions()) {
      byKey.put(p.provisionKey(), p);
    }
    Cached fresh = new Cached(hash, today, view, Map.copyOf(byKey));
    cached = fresh;
    return fresh;
  }

  @Transactional(readOnly = true)
  public String currentVersionHash() {
    return versions.findFirstByOrderByBuiltAtDesc().map(CorpusVersionEntity::versionHash).orElse("");
  }

  @Transactional(readOnly = true)
  public boolean currentCorpusContains(String provisionKey) {
    return provision(provisionKey).isPresent();
  }

  private CorpusView view(CorpusVersionEntity version, LocalDate today) {
    List<InstrumentEntity> instrumentRows =
        instruments.findAllByCorpusVersionIdOrderBySeedCelexAsc(version.id());
    List<UUID> instrumentIds = instrumentRows.stream().map(InstrumentEntity::id).toList();
    List<CorpusRelationshipEntity> links = relationships.findAllByCorpusVersionId(version.id());
    return new CorpusView(
        version.versionHash(),
        instrumentRows.stream().map(row -> new InstrumentView(
            row.seedCelex(),
            row.consolidationCelex(),
            row.title(),
            row.textHash(),
            row.consolidationDate(),
            row.applicationFrom())).toList(),
        provisions.findAllByInstrumentIdInOrderByProvisionKeyAsc(instrumentIds).stream()
            .map(row -> new ProvisionView(
                row.provisionKey(),
                row.article(),
                row.paragraph(),
                row.point(),
                row.annex(),
                row.textExcerpt(),
                CorpusAsOf.statusOn(row.forceStatus(), row.forceFrom(), today).name(),
                row.forceFrom(),
                row.scopeNote()))
            .toList(),
        guidanceDocs.findAllByCorpusVersionIdOrderBySourceKeyAsc(version.id()).stream()
            .map(row -> new GuidanceView(
                row.sourceKey(),
                row.title(),
                row.authorityRank(),
                row.body(),
                links.stream()
                    .filter(link -> row.sourceKey().equals(link.fromKey()))
                    .map(CorpusRelationshipEntity::relation)
                    .findFirst()
                    .orElse("interprets"),
                links.stream()
                    .filter(link -> row.sourceKey().equals(link.fromKey()))
                    .map(CorpusRelationshipEntity::toKey)
                    .findFirst()
                    .orElse(null)))
            .toList(),
        CorpusIngestService.ATTRIBUTION);
  }

  public record CorpusView(
      String corpusVersion,
      List<InstrumentView> instruments,
      List<ProvisionView> provisions,
      List<GuidanceView> guidance,
      String attribution) {
  }

  public record InstrumentView(
      String seedCelex,
      String consolidationCelex,
      String title,
      String textHash,
      LocalDate consolidationDate,
      LocalDate applicationFrom) {
  }

  public record ProvisionView(
      String provisionKey,
      String article,
      String paragraph,
      String point,
      String annex,
      String textExcerpt,
      String forceStatus,
      LocalDate forceFrom,
      String scopeNote) {
  }

  public record GuidanceView(
      String sourceKey,
      String title,
      String authorityRank,
      String body,
      String relation,
      String interpretsProvisionKey) {
  }
}
