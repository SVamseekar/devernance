package os.assurance.eu.api.regmonitor;

import os.assurance.eu.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled poller for regulatory feed sources (similar cadence pattern to eval worker).
 * Config: {@code assurance.reg-monitor.enabled}, {@code assurance.reg-monitor.poll-interval-ms}.
 */
@Component
@ConditionalOnProperty(name = "assurance.reg-monitor.enabled", havingValue = "true", matchIfMissing = true)
public class RegMonitorPoller {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegMonitorPoller.class);

  private final RegMonitorIngestionService ingestionService;
  private final TenantContext tenantContext;

  public RegMonitorPoller(RegMonitorIngestionService ingestionService, TenantContext tenantContext) {
    this.ingestionService = ingestionService;
    this.tenantContext = tenantContext;
  }

  // The first poll waits for startup runners (BootstrapData creates the tenant its audit events belong to).
  @Scheduled(
      fixedDelayString = "${assurance.reg-monitor.poll-interval-ms:60000}",
      initialDelayString = "${assurance.reg-monitor.initial-delay-ms:30000}")
  public void poll() {
    // Audit append requires tenant context; use bootstrap MVP tenant for global feed events.
    tenantContext.setOverrides(TenantContext.DEFAULT_TENANT_ID, TenantContext.SYSTEM_USER_ID);
    try {
      int n = ingestionService.pollDueSources();
      if (n > 0) {
        LOGGER.info("Reg-monitor poll ingested {} new item(s)", n);
      }
    } catch (RuntimeException ex) {
      LOGGER.warn("Reg-monitor poll cycle failed: {}", ex.getMessage());
    } finally {
      tenantContext.clearOverrides();
    }
  }
}
