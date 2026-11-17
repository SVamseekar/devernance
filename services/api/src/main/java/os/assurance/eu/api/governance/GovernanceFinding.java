package os.assurance.eu.api.governance;

/**
 * One problem found by `devernance validate`. {@code code} is stable for scripts; {@code message} is for people.
 * An ERROR fails validation; a WARNING does not.
 */
public record GovernanceFinding(String code, String severity, String system, String subject, String message) {
  public static final String ERROR = "ERROR";
  public static final String WARNING = "WARNING";

  public boolean isError() {
    return ERROR.equals(severity);
  }
}
