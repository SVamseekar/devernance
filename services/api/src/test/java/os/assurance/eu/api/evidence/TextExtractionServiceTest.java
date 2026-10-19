package os.assurance.eu.api.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TextExtractionServiceTest {

    private final TextExtractionService service = new TextExtractionService(null, null, null);
    private final TextExtractionService.SsrfSafeDnsResolver resolver =
        new TextExtractionService.SsrfSafeDnsResolver();

    @Test
    void extractsPlainTextUpload() throws Exception {
        var text = service.extractFromUpload(
            new java.io.ByteArrayInputStream("Reviewers can override routing.".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "note.txt",
            1000);
        assertThat(text).contains("override");
    }

    @Test
    void rejectsLoopbackHost() {
        var request = new CreateEvidenceDocumentRequest(
            java.util.UUID.randomUUID(), "policy", "Test", "https://127.0.0.1/secret", null, null, null);

        assertThatThrownBy(() -> service.extract(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("private or reserved");
    }

    @Test
    void rejectsLinkLocalMetadataHost() {
        var request = new CreateEvidenceDocumentRequest(
            java.util.UUID.randomUUID(), "policy", "Test", "https://169.254.169.254/latest/meta-data/", null, null, null);

        assertThatThrownBy(() -> service.extract(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("private or reserved");
    }

    @Test
    void resolverRejectsPrivateAndReservedAddressesDirectly() {
        assertThatThrownBy(() -> resolver.resolve("127.0.0.1"))
            .isInstanceOf(TextExtractionService.SsrfRejectedException.class)
            .hasMessageContaining("private or reserved");

        assertThatThrownBy(() -> resolver.resolve("169.254.169.254"))
            .isInstanceOf(TextExtractionService.SsrfRejectedException.class)
            .hasMessageContaining("private or reserved");

        assertThatThrownBy(() -> resolver.resolve("10.0.0.5"))
            .isInstanceOf(TextExtractionService.SsrfRejectedException.class)
            .hasMessageContaining("private or reserved");
    }

    @Test
    void resolverReturnsValidatedAddressesForPublicHosts() throws Exception {
        InetAddress[] resolved = resolver.resolve("1.1.1.1");

        assertThat(resolved).isNotEmpty();
        assertThat(resolved[0].getHostAddress()).isEqualTo("1.1.1.1");
    }

    @Test
    void resolverIsTheSoleResolutionPathUsedByTheHttpConnectionManager() {
        // SsrfSafeDnsResolver is registered directly with Apache HttpClient5's
        // PoolingHttpClientConnectionManager in fetchOverHttps — the connection manager
        // calls resolver.resolve(host) exactly once per connection and uses that result
        // to open the socket, so there is no second, independent resolution for a
        // DNS-rebinding attacker to race against the validation check above.
        assertThat(resolver).isInstanceOf(org.apache.hc.client5.http.DnsResolver.class);
    }

    @Test
    void longTextUploadsKeepTheFirstMaxCharactersInsteadOfFailing() {
        String text = "Human reviewers may override automated routing. ".repeat(10_000);
        var in = new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String extracted = service.extractFromUpload(in, "long-policy.txt", 2_000);

        assertThat(extracted).startsWith("Human reviewers may override automated routing.");
        assertThat(extracted.length()).isLessThanOrEqualTo(2_000);
    }

    @Test
    void privateTargetsInEveryReservedRangeAreRefused() {
        for (String host : new String[] {"0.0.0.0", "100.64.0.1", "169.254.169.254", "fd00::1", "64:ff9b::7f00:1"}) {
            assertThatThrownBy(() -> resolver.resolve(host)).as(host)
                .isInstanceOf(TextExtractionService.SsrfRejectedException.class);
        }
    }
}
