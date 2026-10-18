package os.assurance.eu.api.framework;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.billing.Feature;
import os.assurance.eu.api.system.AiSystemRepository;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/frameworks")
public class FrameworkController {
  private final CrosswalkService crosswalks;
  private final EntitlementService entitlements;
  private final AiSystemRepository systems;

  public FrameworkController(CrosswalkService crosswalks, EntitlementService entitlements, AiSystemRepository systems) {
    this.crosswalks = crosswalks;
    this.entitlements = entitlements;
    this.systems = systems;
  }

  @GetMapping("/{framework}")
  public Map<String, Object> coverage(@PathVariable UUID systemId, @PathVariable String framework) {
    Feature feature = CrosswalkService.FEATURES.get(framework);
    if (feature == null || crosswalks.find(framework).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown framework");
    }
    entitlements.requireFeature(feature);
    systems.findById(systemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI system not found"));
    return crosswalks.view(framework, systemId);
  }
}
