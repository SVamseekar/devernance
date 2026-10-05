package os.assurance.eu.api.framework;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import os.assurance.eu.api.billing.EntitlementService;
import os.assurance.eu.api.billing.Feature;

@RestController
@RequestMapping("/api/v1/packs")
public class AuAdmPackController {
  private final AuAdmPackService pack;
  private final EntitlementService entitlements;

  public AuAdmPackController(AuAdmPackService pack, EntitlementService entitlements) {
    this.pack = pack;
    this.entitlements = entitlements;
  }

  @GetMapping("/au-adm")
  public Map<String, Object> auAdm() {
    entitlements.requireFeature(Feature.AU_ADM_PACK);
    return pack.build();
  }
}
