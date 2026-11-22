package os.assurance.eu.api.system;

import java.util.UUID;

/** Published when something the gate reads changed outside the system record, such as an approval cycle. */
public record ReleaseInputsChanged(UUID systemId) {
}
