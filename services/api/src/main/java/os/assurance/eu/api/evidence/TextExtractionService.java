package os.assurance.eu.api.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.tika.metadata.TikaCoreProperties;
import os.assurance.eu.api.tenant.TenantContext;
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
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fetches evidence document content over HTTPS, S3, or inline content.
 *
 * <p>SSRF defense: {@link SsrfSafeDnsResolver} is the only DNS resolution path used to open
 * the outbound socket. It resolves the hostname exactly once, validates the resolved address
 * against private/loopback/link-local/multicast ranges, and returns that single validated
 * address to Apache HttpClient5's connection manager for the actual connect. Because there is
 * no second, independent resolution between validation and connection (unlike the JDK's
 * {@code java.net.http.HttpClient}, which re-resolves the hostname itself at connect time),
 * a DNS-rebinding attacker cannot swap the address after validation passes.
 */
@Service
public class TextExtractionService {
    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);
    private static final int MAX_FETCH_CHARS = 20 * 1024 * 1024;
    /** Same ceiling as uploads, so a URL cannot pull in more than a file upload could. */
    private static final int MAX_FETCH_BYTES = 25 * 1024 * 1024;
    private static final Duration PARSE_TIMEOUT = Duration.ofSeconds(30);
    private final Tika tika = new Tika();
    private final FileStorageService fileStorage;
    private final FileStorageProperties storageProps;
    private final TenantContext tenantContext;
    private final ExecutorService parser = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "tika-upload");
        t.setDaemon(true);
        t.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        return t;
    });

    public TextExtractionService(
            FileStorageService fileStorage,
            FileStorageProperties storageProps,
            TenantContext tenantContext) {
        this.fileStorage = fileStorage;
        this.storageProps = storageProps;
        this.tenantContext = tenantContext;
    }

    /** Extract text from an uploaded file's bytes. Never falls back to a metadata stub. */
    public String extractFromUpload(InputStream content, String filename, int maxChars) {
        String text;
        try {
            text = parseWithTimeout(content.readAllBytes(), filename, maxChars);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "The file took too long to read. Upload a smaller or text-based file.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "The file could not be read. Upload a PDF, DOCX, TXT, MD, or JSON file.");
        }
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "No text could be extracted. Scanned images are not supported; upload a text-based file.");
        }
        return text.strip();
    }

    /**
     * Parses on the bounded Tika pool so one hostile or huge document cannot hold a request thread past
     * {@link #PARSE_TIMEOUT}. Used for uploads and for documents fetched over HTTPS or from S3.
     */
    private String parseWithTimeout(byte[] bytes, String filename, int maxChars) throws Exception {
        ClassLoader appLoader = TextExtractionService.class.getClassLoader();
        Future<String> job = parser.submit(() -> {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(appLoader);
            try {
                Metadata metadata = new Metadata();
                if (filename != null) {
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
                }
                return readWithTika(bytes, metadata, filename, maxChars);
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        });
        try {
            return job.get(PARSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            job.cancel(true);
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    /**
     * Tika 4's zip detector throws ArchiveException on non-archives. Fall back to the
     * format parser named by the file extension when that happens.
     */
    private String readWithTika(byte[] bytes, Metadata metadata, String filename, int maxChars) throws Exception {
        try {
            return tika.parseToString(new java.io.ByteArrayInputStream(bytes), metadata, maxChars);
        } catch (Exception e) {
            if (!isArchiveDetectMiss(e)) {
                throw e;
            }
            BodyContentHandler handler = new BodyContentHandler(maxChars);
            try (var in = org.apache.tika.io.TikaInputStream.get(bytes)) {
                parserFor(filename).parse(in, handler, metadata, new ParseContext());
            }
            return handler.toString();
        }
    }

    private static boolean isArchiveDetectMiss(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.apache.commons.compress.archivers.ArchiveException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Parser parserFor(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return new PDFParser();
        }
        if (lower.endsWith(".docx") || lower.endsWith(".xlsx") || lower.endsWith(".pptx")) {
            return new OOXMLParser();
        }
        return new TXTParser();
    }

    public String extract(CreateEvidenceDocumentRequest request) {
        if (request.content() != null && !request.content().isBlank()) {
            return request.content().strip();
        }
        String scheme = URI.create(request.sourceUri()).getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            String extracted = fetchOverHttps(request);
            if (extracted != null) {
                return extracted;
            }
        }
        if ("s3".equals(scheme)) {
            String extracted = fetchFromS3(request);
            if (extracted != null) {
                return extracted;
            }
        }
        return metadataStub(request);
    }

    private String fetchOverHttps(CreateEvidenceDocumentRequest request) {
        URI uri = URI.create(request.sourceUri());
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source URI: no host");
        }

        SsrfSafeDnsResolver resolver = new SsrfSafeDnsResolver();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(resolver)
            .build();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))
            .setResponseTimeout(Timeout.ofSeconds(30))
            .setRedirectsEnabled(false)
            .build();

        byte[] bytes;
        try (CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build()) {
            HttpGet getRequest = new HttpGet(uri);
            bytes = client.execute(getRequest, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300) {
                    EntityUtils.consumeQuietly(response.getEntity());
                    throw new IOException("HTTP " + status);
                }
                try (InputStream body = response.getEntity().getContent()) {
                    return body.readNBytes(MAX_FETCH_BYTES + 1);
                }
            });
        } catch (SsrfRejectedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", request.sourceUri(), e.getMessage());
            return null;
        } finally {
            connectionManager.close();
        }
        if (bytes.length > MAX_FETCH_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                "The source document is larger than 25 MB. Upload a smaller file.");
        }
        try {
            String extracted = parseWithTimeout(bytes, fileNameOf(uri), MAX_FETCH_CHARS);
            return (extracted != null && !extracted.isBlank()) ? extracted.strip() : null;
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", request.sourceUri(), e.toString());
            return null;
        }
    }

    private static String fileNameOf(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String fetchFromS3(CreateEvidenceDocumentRequest request) {
        if (fileStorage == null) {
            return null;
        }
        URI uri = URI.create(request.sourceUri());
        String bucket = uri.getHost();
        String key = uri.getPath().replaceFirst("^/", "");
        if (storageProps != null && !storageProps.bucket().equals(bucket)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "S3 URI references a bucket that is not configured for this deployment");
        }
        if (tenantContext != null) {
            String expectedPrefix = "evidence/" + tenantContext.tenantId() + "/";
            if (!key.startsWith(expectedPrefix)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "S3 URI is outside this workspace's evidence folder");
            }
        }
        try (InputStream body = fileStorage.download(bucket, key)) {
            byte[] bytes = body.readNBytes(MAX_FETCH_BYTES + 1);
            if (bytes.length > MAX_FETCH_BYTES) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "The source document is larger than 25 MB. Upload a smaller file.");
            }
            String extracted = parseWithTimeout(bytes, fileNameOf(uri), MAX_FETCH_CHARS);
            return (extracted != null && !extracted.isBlank()) ? extracted.strip() : null;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("S3 extraction failed for {}: {}", request.sourceUri(), e.getMessage());
            return null;
        }
    }

    private String metadataStub(CreateEvidenceDocumentRequest request) {
        return """
            Evidence document: %s
            Type: %s
            Source: %s
            This metadata-only evidence record has been indexed. Upload extracted text in the content field to enable richer retrieval.
            """
            .formatted(request.title(), request.type().toUpperCase(Locale.ROOT), request.sourceUri())
            .strip();
    }

    /**
     * Resolves a hostname exactly once per connection and rejects private, loopback,
     * link-local, any-local, or multicast addresses. This is registered directly with
     * Apache HttpClient5's connection manager, so the address it validates is the same
     * address used to open the socket — there is no separate, later resolution for an
     * attacker to race via DNS rebinding.
     */
    static final class SsrfSafeDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (!PublicAddressPolicy.isPublic(addr)) {
                    throw new SsrfRejectedException(
                        "Source URI resolves to a private or reserved address");
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
