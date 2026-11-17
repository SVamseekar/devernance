package os.assurance.eu.api.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The request hash binds an authorization token to exactly the request the policy judged. Two requests that the
 * policy could judge differently must never share a hash.
 */
class GovernanceRequestHashTest {
  private static String hash(Map<String, Object> resource, Map<String, Object> context) {
    return GovernanceAuthorizationService.requestSha256("payment.refund", resource, context);
  }

  @Test
  void numbersAreComparedByValue() {
    String number = hash(Map.of(), Map.of("amount", 75000));
    assertThat(hash(Map.of(), Map.of("amount", 75000L))).isEqualTo(number);
    assertThat(hash(Map.of(), Map.of("amount", 75000.0))).isEqualTo(number);
    assertThat(hash(Map.of(), Map.of("amount", new BigDecimal("75000.00")))).isEqualTo(number);
    assertThat(hash(Map.of(), Map.of("amount", 75001))).isNotEqualTo(number);
  }

  @Test
  void aStringNeverHashesLikeANumber() {
    String number = hash(Map.of(), Map.of("amount", 75000));
    // Including strings shaped like the internal encoding of a number.
    for (String text : List.of("75000", "n:75000", "number:75000", "s:75000")) {
      assertThat(hash(Map.of(), Map.of("amount", text))).as(text).isNotEqualTo(number);
    }
    assertThat(hash(Map.of(), Map.of("flag", true))).isNotEqualTo(hash(Map.of(), Map.of("flag", "true")));
  }

  @Test
  void keyOrderDoesNotMatterButPlacementDoes() {
    assertThat(hash(Map.of("a", "1", "b", "2"), Map.of())).isEqualTo(hash(Map.of("b", "2", "a", "1"), Map.of()));
    assertThat(hash(Map.of("customer_id", "CUST-102"), Map.of()))
        .isNotEqualTo(hash(Map.of(), Map.of("customer_id", "CUST-102")));
  }
}
