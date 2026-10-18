package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.DataContractStatus;
import os.assurance.eu.api.system.ReleaseDecision;
import os.assurance.eu.api.system.RiskClass;

class AuAdmPackServiceTest {
  private static AiSystem system(RiskClass risk, String purpose, String impact, List<String> users, List<String> data) {
    return new AiSystem(UUID.randomUUID(), "Claims triage", "Ops", purpose, risk, "basis", "AU", 0, 0,
        DataContractStatus.HEALTHY, ReleaseDecision.REVIEW, List.of(), "", "", "", data, "insurance", impact,
        users, Instant.EPOCH, Instant.EPOCH);
  }

  @Test
  void aPassingOversightControlSaysAPersonReviewsAndTheProgramSupportsTheDecision() {
    var entry = AuAdmPackService.classify(system(RiskClass.HIGH, "Prioritise claims.", "claim prioritisation",
        List.of("claimants"), List.of("claims history")), true);
    assertThat(entry.status()).isEqualTo("include");
    assertThat(entry.draftStatement())
        .contains("It substantially and directly supports decisions about claim prioritisation for claimants.")
        .endsWith("A person reviews these decisions: yes.");
  }

  @Test
  void everyMissingFieldIsListedAndNothingIsDrafted() {
    var entry = AuAdmPackService.classify(system(RiskClass.HIGH, " ", null, List.of(), List.of()), false);
    assertThat(entry.status()).isEqualTo("needs_input");
    assertThat(entry.missing()).containsExactly("purpose", "decisionImpact", "affectedUsers", "dataSources");
    assertThat(entry.draftStatement()).isNull();
  }

  @Test
  void aMinimalSystemThatAffectsPeopleIsStillAssessed() {
    var entry = AuAdmPackService.classify(system(RiskClass.MINIMAL, "Route emails.", "", List.of("customers"),
        List.of("email text")), false);
    assertThat(entry.status()).isEqualTo("needs_input");
    assertThat(entry.missing()).containsExactly("decisionImpact");
  }
}
