package os.assurance.eu.api.account;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import os.assurance.eu.api.evidence.FileStorageService;

/**
 * Hard-deletes workspaces whose 30-day deletion window has passed. {@link #PURGE_SQL} lists every table that
 * holds tenant data, children before parents; {@code WorkspaceLifecycleApiTest} checks it against the live
 * schema so a new migration cannot silently leave data behind. The workspace export reads the same list.
 */
@Component
public class WorkspacePurgeJob {
  private static final Logger log = LoggerFactory.getLogger(WorkspacePurgeJob.class);

  static final List<String> PURGE_SQL = List.of(
      "delete from evidence_chunks where document_id in (select id from evidence_documents where tenant_id = ?)",
      "delete from determination_obligations where run_id in (select id from determination_runs where tenant_id = ?)",
      "delete from workflow_notifications where tenant_id = ?",
      "delete from approval_stages where tenant_id = ?",
      "delete from approval_workflows where tenant_id = ?",
      "delete from assessment_applicability where tenant_id = ?",
      "delete from evidence_exceptions where tenant_id = ?",
      "delete from mapping_proposals where tenant_id = ?",
      "delete from governance_approvals where tenant_id = ?",
      "delete from governance_decisions where tenant_id = ?",
      "delete from governance_observations where tenant_id = ?",
      "delete from governance_resources where tenant_id = ?",
      "delete from system_change_scope where tenant_id = ?",
      "delete from system_controls where tenant_id = ?",
      "delete from drift_events where tenant_id = ?",
      "delete from data_contracts where tenant_id = ?",
      "delete from eval_runs where tenant_id = ?",
      "delete from eval_datasets where tenant_id = ?",
      "delete from evidence_queries where tenant_id = ?",
      "delete from evidence_documents where tenant_id = ?",
      "delete from conformity_dossiers where tenant_id = ?",
      "delete from determination_runs where tenant_id = ?",
      "delete from reg_item_reviews where tenant_id = ?",
      "delete from audit_events where tenant_id = ?",
      "delete from audit_chain_heads where tenant_id = ?",
      "delete from api_keys where tenant_id = ?",
      "delete from refresh_tokens where tenant_id = ?",
      "delete from user_invites where tenant_id = ?",
      "delete from gate_run_counters where tenant_id = ?",
      "delete from tenant_subscriptions where tenant_id = ?",
      "delete from auth_tokens where user_id in (select id from users where tenant_id = ?)",
      "delete from ai_systems where tenant_id = ?",
      "delete from users where tenant_id = ?",
      "delete from tenants where id = ?");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final FileStorageService storage;
  private final Clock clock;

  public WorkspacePurgeJob(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
      FileStorageService storage, Clock clock) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.storage = storage;
    this.clock = clock;
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
  public void purgeDue() {
    List<UUID> due = jdbc.query(
        "select id from tenants where status = 'DELETION_PENDING' and purge_after < ?",
        (rs, i) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()));
    for (UUID tenantId : due) {
      try {
        purge(tenantId);
        log.info("Purged workspace {}", tenantId);
      } catch (RuntimeException e) {
        // One failing workspace must not block the others; it is retried on the next run.
        log.error("Could not purge workspace {}", tenantId, e);
      }
    }
  }

  /** Deletes every row of one workspace, then its stored files. */
  public void purge(UUID tenantId) {
    transaction.executeWithoutResult(status -> {
      // Row lock: two instances running the job at once take turns instead of racing; the second
      // finds nothing left to delete.
      jdbc.query("select id from tenants where id = ? for update", rs -> { }, tenantId);
      PURGE_SQL.forEach(sql -> jdbc.update(sql, tenantId));
    });
    // Files go only after the rows are gone: a failed purge must not leave rows pointing at missing files.
    storage.deletePrefix("evidence/" + tenantId + "/");
  }
}
