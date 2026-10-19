package os.assurance.eu.api.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import os.assurance.eu.api.evidence.EvidenceDocument;
import os.assurance.eu.api.evidence.EvidenceRepository;
import os.assurance.eu.api.system.EvidencePackResponse;
import os.assurance.eu.api.system.EvidencePackService;

/**
 * One system's evidence as a ZIP a customer can upload as custom evidence to a GRC tool (Vanta, Drata): an index of
 * the indexed documents, the signed JSON pack, and the guide for verifying that pack.
 */
@Service
public class GrcExportService {
  public static final String INDEX = "evidence-index.csv";
  public static final String PACK = "evidence-pack.json";
  public static final String GUIDE = "VERIFY_EVIDENCE_PACK.md";
  static final String FORMAT = "grc-zip";
  static final List<String> COLUMNS = List.of("document_id", "document_title", "type", "created_date", "sha256",
      "pack_content_sha256", "pack_signature_key_id");
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  public record Export(String filename, String contentSha256, byte[] zip) {}

  private final EvidencePackService packs;
  private final EvidenceRepository evidence;
  private final ObjectMapper mapper;
  private final byte[] guide;

  public GrcExportService(EvidencePackService packs, EvidenceRepository evidence, ObjectMapper mapper) {
    this.packs = packs;
    this.evidence = evidence;
    this.mapper = mapper;
    try (InputStream in = new ClassPathResource("grc/" + GUIDE).getInputStream()) {
      this.guide = in.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Missing bundled " + GUIDE, e);
    }
  }

  /**
   * Builds and signs a fresh pack (recorded in the audit ledger as a {@code grc-zip} export) and zips it. Not
   * transactional, for the same reason as {@link EvidencePackService#buildAndExport}.
   */
  public Export build(UUID systemId) {
    EvidencePackResponse pack = packs.buildAndExport(systemId, FORMAT);
    List<EvidenceDocument> indexed = evidence.findDocumentsBySystemId(systemId).stream()
        .filter(document -> CrosswalkService.isIndexed(document.ingestionStatus()))
        .sorted(Comparator.comparing(EvidenceDocument::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(document -> document.id().toString()))
        .toList();
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
        add(zip, INDEX, index(indexed, pack).getBytes(StandardCharsets.UTF_8), pack.generatedAt());
        add(zip, PACK, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pack), pack.generatedAt());
        add(zip, GUIDE, guide, pack.generatedAt());
      }
      String filename = "grc-export-" + systemId + "-" + STAMP.format(pack.generatedAt()) + ".zip";
      return new Export(filename, pack.contentSha256(), bytes.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException("Could not build the GRC export", e);
    }
  }

  static String index(List<EvidenceDocument> documents, EvidencePackResponse pack) {
    String keyId = signatureKeyId(pack.signature());
    StringBuilder csv = new StringBuilder(Csv.BOM).append(Csv.row(COLUMNS));
    for (EvidenceDocument document : documents) {
      csv.append(Csv.row(List.of(
          document.id().toString(),
          document.title(),
          document.type(),
          document.createdAt() == null ? "" : document.createdAt().toString(),
          document.checksum() == null ? "" : document.checksum(),
          pack.contentSha256(),
          keyId)));
    }
    return csv.toString();
  }

  private static String signatureKeyId(String signature) {
    try {
      String keyId = JWSObject.parse(signature).getHeader().getKeyID();
      return keyId == null ? "" : keyId;
    } catch (ParseException e) {
      throw new IllegalStateException("Evidence pack signature is not a JWS", e);
    }
  }

  private static void add(ZipOutputStream zip, String name, byte[] content, Instant at) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(at.toEpochMilli());
    zip.putNextEntry(entry);
    zip.write(content);
    zip.closeEntry();
  }
}
