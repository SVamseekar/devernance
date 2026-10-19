package os.assurance.eu.api.regmonitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import os.assurance.eu.api.net.PublicAddressPolicy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SSRF-safe HTTPS fetch for regulatory feed polling.
 *
 * <p>Reuses the evidence DNS-pin pattern: {@link SsrfSafeDnsResolver} is the only DNS path used
 * to open the outbound socket (Apache HttpClient5). Resolves once, rejects private/loopback/
 * link-local/multicast, and connects with that same resolution — no DNS-rebinding TOCTOU window.
 */
@Component
public class SsrfSafeHttpFetcher {
  private static final Logger log = LoggerFactory.getLogger(SsrfSafeHttpFetcher.class);

  private final RegMonitorProperties properties;

  public SsrfSafeHttpFetcher(RegMonitorProperties properties) {
    this.properties = properties;
  }

  public String fetchHttps(String url) throws IOException {
    URI uri = URI.create(url);
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"https".equals(scheme)) {
      throw new SsrfRejectedException("Only https URLs are allowed for reg-monitor fetch");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new SsrfRejectedException("Invalid URL: no host");
    }

    SsrfSafeDnsResolver resolver = new SsrfSafeDnsResolver();
    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(resolver)
            .build();

    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(Timeout.ofSeconds(properties.getConnectTimeoutSeconds()))
        .setResponseTimeout(Timeout.ofSeconds(properties.getResponseTimeoutSeconds()))
        .setRedirectsEnabled(false)
        .build();

    try (CloseableHttpClient client = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig)
        .disableRedirectHandling()
        .build()) {
      HttpGet getRequest = new HttpGet(uri);
      return client.execute(getRequest, response -> {
        int status = response.getCode();
        if (status < 200 || status >= 300) {
          EntityUtils.consumeQuietly(response.getEntity());
          throw new IOException("HTTP " + status + " fetching " + url);
        }
        try (InputStream body = response.getEntity().getContent()) {
          byte[] bytes = body.readNBytes(properties.getMaxFetchCharacters());
          EntityUtils.consume(response.getEntity());
          return new String(bytes, StandardCharsets.UTF_8);
        }
      });
    } catch (SsrfRejectedException e) {
      throw e;
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Reg-monitor fetch failed for {}: {}", url, e.getMessage());
      throw new IOException("Reg-monitor fetch failed: " + e.getMessage(), e);
    } finally {
      connectionManager.close();
    }
  }

  /**
   * Resolves a hostname exactly once and rejects private/reserved addresses.
   * Package-visible for unit tests (mirrors evidence TextExtractionService).
   */
  static final class SsrfSafeDnsResolver implements DnsResolver {
    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress addr : addresses) {
        if (!PublicAddressPolicy.isPublic(addr)) {
          throw new SsrfRejectedException(
              "Reg-monitor URL resolves to a private or reserved address");
        }
      }
      return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
      return InetAddress.getByName(host).getCanonicalHostName();
    }
  }

  static final class SsrfRejectedException extends UnknownHostException {
    SsrfRejectedException(String message) {
      super(message);
    }
  }
}
