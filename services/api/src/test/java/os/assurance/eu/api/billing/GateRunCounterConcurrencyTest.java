package os.assurance.eu.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;

@SpringBootTest
class GateRunCounterConcurrencyTest {
  @Autowired EntitlementService entitlements;
  @Autowired TenantJpaRepository tenants;
  @Autowired GateRunCounterJpaRepository counters;
  @Autowired TenantContext tenantContext;

  // Repeated because a lost update only shows under some interleavings; each repetition uses a fresh tenant.
  @RepeatedTest(25)
  void parallelFirstRunsOfTheMonthAllCount() throws Exception {
    UUID tenantId = UUID.randomUUID();
    tenants.save(new TenantEntity(tenantId, "Matrix " + tenantId, "starter", "EU", Instant.now()));
    int pipelines = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(pipelines);
    List<Future<Boolean>> results = new ArrayList<>();
    for (int i = 0; i < pipelines; i++) {
      Callable<Boolean> run = () -> {
        start.await();
        tenantContext.setOverrides(tenantId, TenantContext.SYSTEM_USER_ID);
        try {
          entitlements.recordGateRun();
          return true;
        } finally {
          tenantContext.clearOverrides();
        }
      };
      results.add(pool.submit(run));
    }
    start.countDown();
    for (Future<Boolean> result : results) {
      assertThat(result.get()).isTrue();
    }
    pool.shutdown();

    String period = YearMonth.now(ZoneOffset.UTC).toString();
    assertThat(counters.runsIn(tenantId, period)).isEqualTo(pipelines);
  }
}
