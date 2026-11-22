package os.assurance.eu.api.evidence;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ranks in Postgres, so the API never reads a system's chunks to answer a question.
 *
 * <p>Keywords: BM25 over the {@code content_tsv} column (English stemming, GIN index). The GIN index finds the
 * chunks that contain any question term; term and chunk frequencies come from the stored tsvector positions.
 * Plain Postgres full-text ranking ({@code ts_rank}) ignores how rare a term is; BM25 does not.
 *
 * <p>Meaning: exact cosine distance with pgvector over the chunks of the system's documents. A system holds
 * thousands of chunks at most, and the B-tree on {@code document_id} narrows the scan to them, so an exact scan
 * is fast and keeps full recall where an approximate (HNSW) index filtered to one system would lose results.
 * Without pgvector the meaning ranking runs in the API process.
 */
class PostgresEvidenceSearchIndex implements EvidenceSearchIndex {
  private static final String KEYWORD_SQL = """
      with terms as (
        select distinct lexeme from unnest(tsvector_to_array(to_tsvector('english', ?))) as lexeme
      ), query as (
        select string_agg(quote_literal(lexeme), ' | ')::tsquery as q from terms
      ), scope as (
        select c.id, c.content_tsv, c.content_lexemes from evidence_chunks c where c.document_id = any(?)
      ), stats as (
        select count(*)::float8 as n, greatest(avg(content_lexemes), 1)::float8 as avgdl from scope
      ), matches as (
        select s.id, s.content_lexemes::float8 as dl, u.lexeme,
               coalesce(array_length(u.positions, 1), 1)::float8 as tf
        from scope s cross join query cross join lateral unnest(s.content_tsv) as u
        where s.content_tsv @@ query.q and u.lexeme in (select lexeme from terms)
      ), df as (
        select lexeme, count(*)::float8 as df from matches group by lexeme
      )
      select m.id,
             sum(ln(1 + (st.n - d.df + 0.5) / (d.df + 0.5)) * m.tf * (? + 1)
                 / (m.tf + ? * (1 - ? + ? * m.dl / st.avgdl))) as score,
             count(*) as matched
      from matches m join df d on d.lexeme = m.lexeme cross join stats st
      group by m.id
      order by score desc
      limit ?
      """;

  private static final String MEANING_SQL = """
      select c.id, 1 - (c.embedding_vector <=> ?::vector) as score
      from evidence_chunks c
      where c.document_id = any(?) and c.embedding_provider = ? and c.embedding_vector is not null
      order by c.embedding_vector <=> ?::vector
      limit ?
      """;

  private final JdbcTemplate jdbc;
  private final boolean vectorInDatabase;
  private final InMemoryEvidenceSearchIndex inMemory;

  PostgresEvidenceSearchIndex(JdbcTemplate jdbc, boolean vectorInDatabase, InMemoryEvidenceSearchIndex inMemory) {
    this.jdbc = jdbc;
    this.vectorInDatabase = vectorInDatabase;
    this.inMemory = inMemory;
  }

  @Override
  public List<RankedChunk> byKeywords(List<UUID> documentIds, String question, int limit) {
    if (documentIds.isEmpty() || EvidenceTerms.distinct(question).isEmpty()) {
      return List.of();
    }
    return jdbc.query(connection -> {
      PreparedStatement statement = connection.prepareStatement(KEYWORD_SQL);
      statement.setString(1, question);
      statement.setArray(2, uuidArray(connection, documentIds));
      statement.setDouble(3, InMemoryEvidenceSearchIndex.K1);
      statement.setDouble(4, InMemoryEvidenceSearchIndex.K1);
      statement.setDouble(5, InMemoryEvidenceSearchIndex.B);
      statement.setDouble(6, InMemoryEvidenceSearchIndex.B);
      statement.setInt(7, limit);
      return statement;
    }, (row, index) -> new RankedChunk(row.getObject(1, UUID.class), row.getDouble(2), row.getInt(3)));
  }

  @Override
  public List<RankedChunk> byMeaning(List<UUID> documentIds, String provider, String questionEmbedding, int limit) {
    if (!vectorInDatabase) {
      return inMemory.byMeaning(documentIds, provider, questionEmbedding, limit);
    }
    if (documentIds.isEmpty()) {
      return List.of();
    }
    String vector = "[" + questionEmbedding + "]";
    return jdbc.query(connection -> {
      PreparedStatement statement = connection.prepareStatement(MEANING_SQL);
      statement.setString(1, vector);
      statement.setArray(2, uuidArray(connection, documentIds));
      statement.setString(3, provider);
      statement.setString(4, vector);
      statement.setInt(5, limit);
      return statement;
    }, (row, index) -> new RankedChunk(row.getObject(1, UUID.class), row.getDouble(2), 0));
  }

  @Override
  public String description() {
    return vectorInDatabase ? "postgres-bm25+pgvector" : "postgres-bm25+in-memory-vectors";
  }

  private static Array uuidArray(java.sql.Connection connection, List<UUID> ids) throws java.sql.SQLException {
    return connection.createArrayOf("uuid", ids.toArray());
  }
}
