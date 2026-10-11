package os.assurance.eu.api.framework;

import java.util.List;

/**
 * A due-diligence question bank loaded from {@code classpath:questionnaires/*.json}. Each answer template only
 * names facts ({@code {system.modelName}}) that the export fills from the system's records; {@code evidence} lists
 * what backs the answer: {@code control:CODE}, {@code evidence:TYPE}, or {@code pack:path} into the signed pack.
 */
public record Questionnaire(String id, String version, String title, List<Question> questions) {
  public Questionnaire {
    questions = questions == null ? List.of() : List.copyOf(questions);
  }

  public record Question(String id, String topic, String question, String answerTemplate, List<String> evidence) {
    public Question {
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
  }
}
