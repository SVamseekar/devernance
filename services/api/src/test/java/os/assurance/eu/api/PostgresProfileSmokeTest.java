package os.assurance.eu.api;

import static org.assertj.core.api.Assertions.assertThat;

import os.assurance.eu.api.demo.DemoProperties;
import os.assurance.eu.api.evidence.Citation;
import os.assurance.eu.api.evidence.EvidenceQueryResponse;
import os.assurance.eu.api.evidence.EvidenceRetriever;
import os.assurance.eu.api.evidence.EvidenceService;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "assurance.eval.callback.secret=ci-eval-callback-secret-for-postgres-smoke-only",
    "assurance.audit.chain-secret=ci-audit-chain-secret-for-postgres-smoke-only",
    "assurance.oauth.state-secret=ci-oauth-state-secret-for-postgres-smoke-only",
    "assurance.auth.key-encryption-secret=ci-jwt-key-encryption-secret-for-postgres-smoke",
    // The postgres profile refuses log-mode email; the sender is built but never connects in this test.
    "assurance.email.mode=smtp",
    "assurance.email.primary.host=smtp.invalid",
    // Seeds a system with two indexed documents, so the SQL evidence search has something to rank.
    "assurance.demo.enabled=true"
})
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_SMOKE", matches = "true")
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+")
class PostgresProfileSmokeTest {
  @Autowired
  private AiSystemRepository systems;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private TenantContext tenantContext;
  @Autowired
  private EvidenceService evidence;
  @Autowired
  private EvidenceRetriever retriever;

  @Test
  void startsWithPostgresProfileAndRunsFlywayBootstrap() {
    assertThat(tenantContext.withTenant(TenantContext.DEFAULT_TENANT_ID, systems::findAll)).isNotNull();
    assertThat(tenants.findById(TenantContext.DEFAULT_TENANT_ID)).isPresent();
  }

  @Test
  void evidenceSearchRanksInPostgresAndCitesTheMatchingDocument() {
    // CI's pgvector image ranks both ways in SQL; a Postgres without the extension ranks meaning in the API.
    assertThat(retriever.searchDescription()).startsWith("postgres-bm25+");
    tenantContext.setOverrides(DemoProperties.DEMO_TENANT_ID, DemoProperties.DEMO_USER_ID);
    try {
      AiSystem system = systems.findAll().stream()
          .filter(s -> "Claims Triage AI (demo)".equals(s.name()))
          .findFirst()
          .orElseThrow();

      EvidenceQueryResponse response = evidence.answer(system, "Within how many days can claimants appeal?");

      assertThat(response.citations()).isNotEmpty();
      Citation first = response.citations().get(0);
      assertThat(first.title()).isEqualTo("Human oversight SOP");
      assertThat(first.snippet()).contains("appeal within 30 days");
    } finally {
      tenantContext.clearOverrides();
    }
  }
}
