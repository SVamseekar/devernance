package os.assurance.eu.api.governance;

/** The answer to "may this agent do this?". REVIEW means a person must approve before the action may run. */
public enum DecisionStatus {
  ALLOW,
  DENY,
  REVIEW
}
