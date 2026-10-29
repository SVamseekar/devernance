package os.assurance.eu.api.contract;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code note} says what was done about the drift; acknowledging or resolving without one is refused. */
public record UpdateDriftEventRequest(@NotNull DriftStatus status, @Size(max = 2048) String note) {

  public UpdateDriftEventRequest(DriftStatus status) {
    this(status, null);
  }
}
