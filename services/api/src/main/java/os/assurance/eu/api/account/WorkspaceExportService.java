package os.assurance.eu.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import os.assurance.eu.api.audit.AuditService;

/** Builds a zip with one JSON file per tenant table, read with the same predicates the purge uses. */
@Service
public class WorkspaceExportService {
  private static final Set<String> SECRET_COLUMNS = Set.of("password_hash", "key_hash", "token_hash", "replaced_by_token_hash");

  private final JdbcTemplate jdbc;
  private final JdbcTemplate streaming;
  private final TransactionTemplate reads;
  private final ObjectMapper json;
  private final AuditService audit;
  private final Clock clock;

  public WorkspaceExportService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit, Clock clock,
      PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.streaming = new JdbcTemplate(jdbc.getDataSource());
    this.streaming.setFetchSize(500);
    this.reads = new TransactionTemplate(transactionManager);
    this.reads.setReadOnly(true);
    this.json = json;
    this.audit = audit;
    this.clock = clock;
  }

  /** Records the export in the audit trail. Runs in the request, before the body is streamed. */
  @Transactional
  public void recordExport(UUID tenantId) {
    audit.append(null, "account.exported", "tenant", tenantId.toString(), Map.of());
  }

  /**
   * Streams the zip to {@code out} one table and one row at a time, so memory use does not grow with the size of
   * the workspace. Runs outside the request thread's transaction, so it opens a read-only one of its own.
   */
  public void writeTo(UUID tenantId, OutputStream out) {
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      write(zip, "README.txt", ("Devernance workspace export. One JSON file per table. Generated "
          + clock.instant() + ".\n").getBytes(StandardCharsets.UTF_8));
      for (String delete : WorkspacePurgeJob.PURGE_SQL) {
        String select = delete.replaceFirst("^delete from", "select * from");
        String table = delete.split("\\s+")[2];
        zip.putNextEntry(new ZipEntry(table + ".json"));
        JsonGenerator generator = json.getFactory().createGenerator(new NonClosing(zip));
        generator.useDefaultPrettyPrinter();
        generator.writeStartArray();
        reads.executeWithoutResult(status -> streaming.query(select, rs -> {
          try {
            generator.writeObject(withoutSecrets(rowOf(rs)));
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }, tenantId));
        generator.writeEndArray();
        generator.close();
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Object> rowOf(java.sql.ResultSet rs) {
    try {
      java.sql.ResultSetMetaData meta = rs.getMetaData();
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 1; i <= meta.getColumnCount(); i++) {
        row.put(meta.getColumnLabel(i), rs.getObject(i));
      }
      return row;
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Lets the JSON generator finish an entry without closing the zip underneath it. */
  private static final class NonClosing extends java.io.FilterOutputStream {
    NonClosing(OutputStream out) {
      super(out);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
      flush();
    }
  }

  private static Map<String, Object> withoutSecrets(Map<String, Object> row) {
    Map<String, Object> clean = new LinkedHashMap<>();
    row.forEach((column, value) -> {
      String name = column.toLowerCase(Locale.ROOT);
      if (!SECRET_COLUMNS.contains(name)) {
        clean.put(name, value);
      }
    });
    return clean;
  }

  private static void write(ZipOutputStream zip, String name, byte[] content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
    zip.closeEntry();
  }
}
