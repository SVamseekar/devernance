package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import os.assurance.eu.api.control.ControlService;
import os.assurance.eu.api.control.ControlStatus;

class CrosswalkServiceTest {
  private static final Set<String> KNOWN = Set.copyOf(ControlService.baselineCodes());

  private static Crosswalk nist() {
    return CrosswalkService.load(new ObjectMapper()).stream()
        .filter(cw -> "nist-ai-rmf".equals(cw.framework()))
        .findFirst()
        .orElseThrow();
  }

  private static CrosswalkService.ItemStatus item(List<CrosswalkService.ItemStatus> items, String id) {
    return items.stream().filter(i -> id.equals(i.id())).findFirst().orElseThrow();
  }

  @Test
  void theBundledNistCrosswalkHasEveryCategoryAndValidates() {
    Crosswalk cw = nist();
    assertThat(cw.items()).hasSize(19);
    assertThat(cw.items()).extracting(Crosswalk.Item::id)
        .contains("GOVERN 1", "GOVERN 6", "MAP 5", "MEASURE 4", "MANAGE 4");
    assertThat(cw.source()).startsWith("https://nvlpubs.nist.gov/");
    CrosswalkService.validate(cw, KNOWN);
  }

  @Test
  void anUnknownControlCodeFailsValidationNamingTheCode() {
    Crosswalk bad = new Crosswalk("nist-ai-rmf", "1.0", "src", "2026-10-05", "",
        List.of(new Crosswalk.Item("GOVERN 1", "t", List.of("NOT_A_CONTROL"), List.of("POLICY"))));
    assertThatThrownBy(() -> CrosswalkService.validate(bad, KNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NOT_A_CONTROL");
  }

  @Test
  void anUnknownEvidenceTypeFailsValidation() {
    Crosswalk bad = new Crosswalk("nist-ai-rmf", "1.0", "src", "2026-10-05", "",
        List.of(new Crosswalk.Item("GOVERN 1", "t", List.of("RISK_MANAGEMENT"), List.of("SELFIE"))));
    assertThatThrownBy(() -> CrosswalkService.validate(bad, KNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SELFIE");
  }

  @Test
  void aPassingControlWithIndexedEvidenceIsCovered() {
    var items = CrosswalkService.coverage(nist(), Map.of("HUMAN_OVERSIGHT", ControlStatus.PASS), Set.of("POLICY"));
    var govern2 = item(items, "GOVERN 2");
    assertThat(govern2.status()).isEqualTo("covered");
    assertThat(govern2.controls()).containsExactly("HUMAN_OVERSIGHT");
    assertThat(govern2.evidence()).containsExactly("POLICY");
    assertThat(govern2.requiredControls()).containsExactly("HUMAN_OVERSIGHT");
  }

  @Test
  void aPassingControlWithoutListedEvidenceIsPartial() {
    var items = CrosswalkService.coverage(nist(), Map.of("HUMAN_OVERSIGHT", ControlStatus.PASS), Set.of("DPIA"));
    assertThat(item(items, "GOVERN 2").status()).isEqualTo("partial");
  }

  @Test
  void indexedEvidenceWithoutAPassingControlIsPartial() {
    var items = CrosswalkService.coverage(nist(), Map.of("HUMAN_OVERSIGHT", ControlStatus.REVIEW), Set.of("POLICY"));
    assertThat(item(items, "GOVERN 2").status()).isEqualTo("partial");
  }

  @Test
  void aSystemWithNothingAttachedListsEveryItemAsNoEvidence() {
    var items = CrosswalkService.coverage(nist(), Map.of(), Set.of());
    assertThat(items).hasSize(19);
    assertThat(items).allSatisfy(i -> {
      assertThat(i.status()).isEqualTo("no_evidence");
      assertThat(i.controls()).isEmpty();
      assertThat(i.requiredEvidence()).isNotEmpty();
    });
  }
}
