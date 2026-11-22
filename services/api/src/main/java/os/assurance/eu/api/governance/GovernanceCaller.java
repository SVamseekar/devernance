package os.assurance.eu.api.governance;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import os.assurance.eu.api.tenant.TenantContextFilter;

/**
 * Who is calling, as written into governance records. For an API key it is the key's name (name the key after
 * the workload, e.g. {@code claims-agent}); for a signed-in person it is {@code user:<id>}. The name comes from
 * the authenticated credential, never from the request body, so a caller cannot claim to be another agent.
 */
public record GovernanceCaller(String agent, UUID apiKeyId) {
  public static GovernanceCaller from(HttpServletRequest request, UUID actorId) {
    Object keyId = request.getAttribute(TenantContextFilter.API_KEY_ID_ATTRIBUTE);
    if (keyId instanceof UUID id) {
      Object name = request.getAttribute(TenantContextFilter.API_KEY_NAME_ATTRIBUTE);
      String agent = name instanceof String text && !text.isBlank() ? text : "api-key:" + id;
      return new GovernanceCaller(agent.length() > 128 ? agent.substring(0, 128) : agent, id);
    }
    return new GovernanceCaller("user:" + actorId, null);
  }

  public boolean isApiKey() {
    return apiKeyId != null;
  }
}
