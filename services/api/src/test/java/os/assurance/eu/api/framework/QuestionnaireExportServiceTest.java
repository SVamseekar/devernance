package os.assurance.eu.api.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import os.assurance.eu.api.control.ControlService;

class QuestionnaireExportServiceTest {
  private static final Set<String> KNOWN = Set.copyOf(ControlService.baselineCodes());

  private static Questionnaire bundled() {
    return QuestionnaireExportService.load(new ObjectMapper());
  }

  private static Questionnaire.Question question(String topic) {
    return bundled().questions().stream().filter(q -> topic.equals(q.topic())).findFirst().orElseThrow();
  }

  private static Questionnaire with(Questionnaire.Question question) {
    return new Questionnaire("test", "1.0", "Test", List.of(question));
  }

  @Test
  void theBundledQuestionnaireCoversTheCommonDueDiligenceTopicsAndValidates() {
    Questionnaire questionnaire = bundled();
    assertThat(questionnaire.id()).isEqualTo("ai-vendor-core");
    assertThat(questionnaire.questions()).hasSize(25);
    assertThat(questionnaire.questions()).extracting(Questionnaire.Question::topic)
        .doesNotHaveDuplicates()
        .contains("model-provenance", "training-data-rights", "human-oversight", "change-control",
            "eval-coverage", "incident-response", "audit-trail", "data-retention", "subprocessors",
            "eu-ai-act-risk-class", "nist-alignment");
    QuestionnaireExportService.validate(questionnaire, KNOWN);
  }

  @Test
  void noTemplateMakesAClaimTheProductMustNotMake() {
    assertThat(bundled().questions()).allSatisfy(q -> assertThat(q.answerTemplate().toLowerCase())
        .doesNotContain("compliant", "certified", "guarantee", "conformity", "notified body"));
  }

  @Test
  void aPlaceholderThatIsNotAKnownFactFailsValidationNamingIt() {
    Questionnaire bad = with(new Questionnaire.Question("Q1", "t", "Why?", "Because {system.madeUp}.", List.of()));
    assertThatThrownBy(() -> QuestionnaireExportService.validate(bad, KNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("system.madeUp");
  }

  @Test
  void aControlThatDoesNotExistFailsValidationNamingIt() {
    Questionnaire inTemplate = with(new Questionnaire.Question(
        "Q1", "t", "Why?", "Passing since {control.NOT_A_CONTROL.passedOn}.", List.of()));
    assertThatThrownBy(() -> QuestionnaireExportService.validate(inTemplate, KNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NOT_A_CONTROL");
    Questionnaire inEvidence = with(new Questionnaire.Question(
        "Q1", "t", "Why?", "Static.", List.of("control:NOT_A_CONTROL")));
    assertThatThrownBy(() -> QuestionnaireExportService.validate(inEvidence, KNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NOT_A_CONTROL");
  }

  @Test
  void anEvidenceReferenceOutsideThePackOrTheEvidenceTypesFailsValidation() {
    assertThatThrownBy(() -> QuestionnaireExportService.validate(
        with(new Questionnaire.Question("Q1", "t", "Why?", "Static.", List.of("evidence:SELFIE"))), KNOWN))
        .hasMessageContaining("SELFIE");
    assertThatThrownBy(() -> QuestionnaireExportService.validate(
        with(new Questionnaire.Question("Q1", "t", "Why?", "Static.", List.of("pack:noSuchSection.x"))), KNOWN))
        .hasMessageContaining("noSuchSection");
  }

  @Test
  void theOversightAnswerNamesThePolicyAndTheDateTheControlPassed() {
    var answer = QuestionnaireExportService.answer(question("human-oversight"), Map.of(
        "control.HUMAN_OVERSIGHT.passedOn", "2026-10-09",
        "evidence.POLICY.titles", "Human oversight policy v3"));
    assertThat(answer.status()).isEqualTo("answered");
    assertThat(answer.missing()).isEmpty();
    assertThat(answer.answer()).contains("Human oversight policy v3").contains("2026-10-09");
  }

  @Test
  void aMissingFactGivesTheManualTextAndNeverAPartialAnswer() {
    var answer = QuestionnaireExportService.answer(question("human-oversight"), Map.of(
        "control.HUMAN_OVERSIGHT.passedOn", "2026-10-09"));
    assertThat(answer.status()).isEqualTo("manual");
    assertThat(answer.answer()).isEqualTo("Not recorded in Devernance — answer manually.");
    assertThat(answer.missing()).containsExactly("evidence.POLICY.titles");
  }

  @Test
  void aBlankFactCountsAsMissing() {
    var answer = QuestionnaireExportService.answer(
        new Questionnaire.Question("Q1", "t", "Where?", "Region: {system.deploymentRegion}.", List.of()),
        Map.of("system.deploymentRegion", "  "));
    assertThat(answer.status()).isEqualTo("manual");
    assertThat(answer.missing()).containsExactly("system.deploymentRegion");
  }

  @Test
  void aFactThatLooksLikeAPlaceholderIsNotExpandedAgain() {
    var answer = QuestionnaireExportService.answer(
        new Questionnaire.Question("Q1", "t", "Who?", "Owner: {system.owner}. Vendor: {system.vendorName}.", List.of()),
        Map.of("system.owner", "{system.vendorName}", "system.vendorName", "Acme"));
    assertThat(answer.answer()).isEqualTo("Owner: {system.vendorName}. Vendor: Acme.");
  }

  @Test
  void csvQuotesCommasQuotesAndLineBreaks() {
    String csv = QuestionnaireExportService.toCsv(List.of(new QuestionnaireExportService.AnswerRow(
        "Q1", "topic", "Who, exactly?", "Policy \"Oversight, v3\"\nsecond line", "answered", List.of(), List.of())));
    String[] lines = csv.split("\r\n", 2);
    assertThat(lines[0]).isEqualTo("﻿id,topic,question,answer,status,evidence");
    assertThat(lines[1]).isEqualTo(
        "Q1,topic,\"Who, exactly?\",\"Policy \"\"Oversight, v3\"\"\nsecond line\",answered,\r\n");
  }

  @Test
  void csvNeutralisesCellsASpreadsheetWouldRunAsAFormula() {
    String csv = QuestionnaireExportService.toCsv(List.of(new QuestionnaireExportService.AnswerRow(
        "Q1", "topic", "=HYPERLINK(\"http://evil\")", "+1", "answered",
        List.of(Map.of("kind", "document", "ref", "POLICY", "title", "@cmd")), List.of())));
    String row = csv.split("\r\n")[1];
    assertThat(row).startsWith("Q1,topic,\"'=HYPERLINK(\"\"http://evil\"\")\",'+1,answered,");
    assertThat(row).doesNotContain(",=").doesNotContain(",+").doesNotContain(",@");
  }
}
