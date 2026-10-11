package os.assurance.eu.api.framework;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.billing.Feature;
import os.assurance.eu.api.system.AiSystemRepository;

@RestController
@RequestMapping("/api/v1/systems/{systemId}")
public class QuestionnaireExportController {
  private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

  private final QuestionnaireExportService questionnaires;
  private final EntitlementService entitlements;
  private final AiSystemRepository systems;

  public QuestionnaireExportController(QuestionnaireExportService questionnaires, EntitlementService entitlements,
      AiSystemRepository systems) {
    this.questionnaires = questionnaires;
    this.entitlements = entitlements;
    this.systems = systems;
  }

  @GetMapping("/questionnaire-export")
  public ResponseEntity<?> export(@PathVariable UUID systemId, @RequestParam(defaultValue = "json") String format) {
    if (!"json".equals(format) && !"csv".equals(format)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be json or csv");
    }
    entitlements.requireFeature(Feature.QUESTIONNAIRE_EXPORT);
    systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    if ("csv".equals(format)) {
      String filename = "questionnaire-" + questionnaires.questionnaireId() + "-" + systemId + ".csv";
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
          .contentType(CSV)
          .body(questionnaires.exportCsv(systemId).getBytes(StandardCharsets.UTF_8));
    }
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(questionnaires.export(systemId));
  }
}
