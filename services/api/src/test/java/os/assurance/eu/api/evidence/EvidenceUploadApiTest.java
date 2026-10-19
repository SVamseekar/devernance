package os.assurance.eu.api.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "assurance.eval.worker.enabled=false",
    "assurance.eval.callback.secret=test-eval-callback-secret"
})
@AutoConfigureMockMvc
class EvidenceUploadApiTest {
  private static final String API_KEY = "00000000-0000-0000-0000-000000000a01";
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired EvidenceDocumentJpaRepository documents;

  private UUID createSystem() throws Exception {
    String body = mockMvc.perform(post("/api/v1/systems")
            .header("X-Api-Key", API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Upload Test %s","owner":"Ops","purpose":"Route claims",
                 "riskClass":"HIGH","riskBasis":"Annex III access to essential services",
                 "deploymentRegion":"EU"}
                """.formatted(UUID.randomUUID())))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(objectMapper.readTree(body).get("id").asText());
  }

  private byte[] fixture(String name) throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/evidence", name));
  }

  @Test
  void pdfUploadIndexesExtractedTextNotAMetadataStub() throws Exception {
    UUID systemId = createSystem();
    MockMultipartFile file = new MockMultipartFile(
        "file", "oversight-sop.pdf", "application/pdf", fixture("oversight-sop.pdf"));
    mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "Oversight SOP")
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ingestionStatus").value("indexed"));

    var query = mockMvc.perform(post("/api/v1/evidence/query")
            .header("X-Api-Key", API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"systemId\":\"" + systemId + "\",\"question\":\"Can reviewers override routing?\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(query).contains("override");
    assertThat(query).doesNotContain("metadata-only evidence record");
  }

  @Test
  void storageKeyIsScopedToTenant() throws Exception {
    UUID systemId = createSystem();
    MockMultipartFile file = new MockMultipartFile(
        "file", "oversight-sop.txt", "text/plain", fixture("oversight-sop.txt"));
    String body = mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "Oversight SOP txt")
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    assertThat(objectMapper.readTree(body).get("sourceUri").asText())
        .contains("/evidence/00000000-0000-0000-0000-000000000001/" + systemId + "/");
  }

  @Test
  void fileWithoutTextIsRejected() throws Exception {
    UUID systemId = createSystem();
    MockMultipartFile file = new MockMultipartFile(
        "file", "blank.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
    mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "Blank")
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void createDocumentRejectsForeignS3Key() throws Exception {
    UUID systemId = createSystem();
    mockMvc.perform(post("/api/v1/evidence/documents")
            .header("X-Api-Key", API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"systemId":"%s","type":"POLICY","title":"Foreign",
                 "sourceUri":"s3://eu-ai-assurance-evidence/evidence/11111111-1111-1111-1111-111111111111/x/y/z.pdf"}
                """.formatted(systemId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void fileLargerThan25MbIsRejected() throws Exception {
    UUID systemId = createSystem();
    byte[] bytes = new byte[26 * 1024 * 1024];
    bytes[0] = 'a';
    MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", bytes);
    mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "Too big")
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void recordsTheServerDigestAndRefusesAMismatchedChecksum() throws Exception {
    UUID systemId = createSystem();
    byte[] bytes = fixture("oversight-sop.txt");
    String expected = java.util.HexFormat.of().formatHex(
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    MockMultipartFile file = new MockMultipartFile("file", "oversight-sop.txt", "text/plain", bytes);

    mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "Claimed digest")
            .param("checksum", "sha256-" + "0".repeat(64))
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isBadRequest());

    mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "Matching digest")
            .param("checksum", expected.toUpperCase())
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.checksum").value("sha256-" + expected));

    mockMvc.perform(multipart("/api/v1/evidence/documents/upload")
            .file(file)
            .param("systemId", systemId.toString())
            .param("type", "POLICY")
            .param("title", "No digest given")
            .header("X-Api-Key", API_KEY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.checksum").value("sha256-" + expected));
  }
}
