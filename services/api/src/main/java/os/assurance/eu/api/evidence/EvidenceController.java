package os.assurance.eu.api.evidence;

import os.assurance.eu.api.billing.EntitlementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.util.StringUtils;
import os.assurance.eu.api.observability.NfrMetrics;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import os.assurance.eu.api.tenant.TenantAuthorizationService;
import os.assurance.eu.api.tenant.TenantContext;
import os.assurance.eu.api.tenant.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {
  private final EntitlementService entitlements;
  private final AiSystemRepository systems;
  private final EvidenceService evidenceService;
  private final FileStorageService fileStorage;
  private final TenantAuthorizationService authorizationService;
  private final NfrMetrics nfrMetrics;
  private final TextExtractionService textExtraction;
  private final TenantContext tenantContext;
  private final EvidenceProperties properties;

  public EvidenceController(
      AiSystemRepository systems,
      EvidenceService evidenceService,
      FileStorageService fileStorage,
      TenantAuthorizationService authorizationService,
      NfrMetrics nfrMetrics,
      TextExtractionService textExtraction,
      TenantContext tenantContext,
      EvidenceProperties properties,
      EntitlementService entitlements) {
    this.entitlements = entitlements;
    this.systems = systems;
    this.evidenceService = evidenceService;
    this.fileStorage = fileStorage;
    this.authorizationService = authorizationService;
    this.nfrMetrics = nfrMetrics;
    this.textExtraction = textExtraction;
    this.tenantContext = tenantContext;
    this.properties = properties;
  }

  @PostMapping("/documents")
  @ResponseStatus(HttpStatus.CREATED)
  public EvidenceDocumentResponse createDocument(@Valid @RequestBody CreateEvidenceDocumentRequest request) {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);
    systems.findById(request.systemId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    entitlements.requireSystemWritable(request.systemId());
    return evidenceService.ingest(request);
  }

  @PostMapping("/documents/upload")
  @ResponseStatus(HttpStatus.CREATED)
  public EvidenceDocumentResponse uploadDocument(
          @RequestParam UUID systemId,
          @RequestParam String type,
          @RequestParam String title,
          @RequestPart("file") MultipartFile file,
          @RequestParam(required = false) String checksum) throws Exception {
    authorizationService.requireAnyRole(
        UserRole.ADMIN, UserRole.AI_ENGINEERING_LEAD, UserRole.COMPLIANCE_OFFICER);
    systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    entitlements.requireSystemWritable(systemId);
    if (file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
    }
    if (file.getSize() > 25L * 1024 * 1024) {
      throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Files up to 25 MB are supported.");
    }
    String filename = StringUtils.hasText(file.getOriginalFilename())
        ? StringUtils.cleanPath(file.getOriginalFilename()).replaceAll("[^A-Za-z0-9._-]", "_")
        : "upload";
    String digest = "sha256-" + sha256Hex(file);
    // The server computes the digest of the bytes it received; a client-supplied checksum can only confirm it.
    if (checksum != null && !checksum.isBlank()
        && !digest.equalsIgnoreCase(checksum.strip())
        && !digest.substring("sha256-".length()).equalsIgnoreCase(checksum.strip())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "The checksum does not match the uploaded file. Expected a SHA-256 of the file.");
    }
    String text;
    try (var in = file.getInputStream()) {
      text = textExtraction.extractFromUpload(in, filename, properties.maxContentCharacters());
    }
    String key = "evidence/" + tenantContext.tenantId() + "/" + systemId + "/" + UUID.randomUUID() + "/" + filename;
    String uri;
    try (var in = file.getInputStream()) {
      uri = fileStorage.upload(key, in, file.getSize(), file.getContentType());
    }
    var req = new CreateEvidenceDocumentRequest(systemId, type, title, uri, text, digest, null);
    return evidenceService.ingest(req);
  }

  private static String sha256Hex(MultipartFile file) throws Exception {
    java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
    try (var in = new java.security.DigestInputStream(file.getInputStream(), sha)) {
      in.transferTo(java.io.OutputStream.nullOutputStream());
    }
    return java.util.HexFormat.of().formatHex(sha.digest());
  }

  @GetMapping("/systems/{systemId}/documents")
  public List<EvidenceDocumentResponse> listDocuments(@PathVariable UUID systemId) {
    systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    return evidenceService.listDocuments(systemId);
  }

  @PostMapping("/query")
  public EvidenceQueryResponse queryEvidence(@Valid @RequestBody EvidenceQueryRequest request) {
    return nfrMetrics.recordEvidenceQuery(() -> {
      AiSystem system = systems.findById(request.systemId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
      return evidenceService.answer(system, request.question());
    });
  }
}
