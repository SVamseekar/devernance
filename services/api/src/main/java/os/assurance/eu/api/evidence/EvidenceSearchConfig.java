package os.assurance.eu.api.evidence;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Picks where evidence ranking runs from what the database offers. The columns it looks for are added by the
 * Postgres-only migration V32_1; H2 has neither, so local runs and tests rank in memory.
 */
@Configuration
class EvidenceSearchConfig {
  private static final Logger log = LoggerFactory.getLogger(EvidenceSearchConfig.class);

  @Bean
  EvidenceSearchIndex evidenceSearchIndex(
      DataSource dataSource,
      JdbcTemplate jdbc,
      EvidenceRepository repository,
      EvidenceEmbeddingService embeddings) throws Exception {
    InMemoryEvidenceSearchIndex inMemory = new InMemoryEvidenceSearchIndex(repository, embeddings);
    boolean postgres;
    try (Connection connection = dataSource.getConnection()) {
      postgres = "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }
    if (!postgres || !hasColumn(jdbc, "content_tsv")) {
      log.info("Evidence search ranks in memory (no Postgres full-text column)");
      return inMemory;
    }
    boolean vectors = hasColumn(jdbc, "embedding_vector");
    if (!vectors) {
      log.warn("pgvector is not installed: evidence meaning ranking reads chunk vectors into the API. "
          + "Install the vector extension and rerun migrations to rank in the database.");
    }
    EvidenceSearchIndex index = new PostgresEvidenceSearchIndex(jdbc, vectors, inMemory);
    log.info("Evidence search ranks with {}", index.description());
    return index;
  }

  private static boolean hasColumn(JdbcTemplate jdbc, String column) {
    Integer count = jdbc.queryForObject(
        "select count(*) from information_schema.columns where table_name = 'evidence_chunks' and column_name = ?",
        Integer.class,
        column);
    return count != null && count > 0;
  }
}
