package os.assurance.eu.api.governance;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import os.assurance.eu.api.system.RiskClass;

/**
 * The approved state of one AI system, from the {@code spec} of an {@code AISystem} manifest: who owns it, which
 * model and prompt it runs, and which tools it has. Deployments are compared against this to find drift.
 */
public record SystemSpec(
    String owner,
    String purpose,
    RiskClass riskClass,
    String riskBasis,
    String deploymentRegion,
    String sector,
    String modelProvider,
    String modelName,
    String modelVersion,
    String promptHash,
    List<String> tools) {

  static final Pattern TOOL_NAME = Pattern.compile("^[A-Za-z0-9_.:-]{1,128}$");
  private static final Set<String> KEYS = Set.of(
      "owner", "purpose", "riskClass", "riskBasis", "deploymentRegion", "sector", "model", "promptHash", "tools");
  private static final Set<String> MODEL_KEYS = Set.of("provider", "name", "version");

  public SystemSpec {
    tools = tools == null ? List.of() : List.copyOf(tools);
  }

  /** {@code name@version}, {@code name} when no version is declared, or null when no model is declared. */
  public String modelRef() {
    if (modelName == null) {
      return null;
    }
    return modelVersion == null ? modelName : modelName + "@" + modelVersion;
  }

  /** Reads and checks a spec. Problems are added to {@code errors}; the result is only usable when none were. */
  public static SystemSpec parse(Map<String, Object> spec, List<String> errors) {
    SpecReader read = new SpecReader(errors);
    read.onlyKeys("spec", spec, KEYS);
    String owner = read.string("spec", spec, "owner", true, 255);
    String purpose = read.string("spec", spec, "purpose", true, 255);
    String riskBasis = read.string("spec", spec, "riskBasis", true, 255);
    String region = read.string("spec", spec, "deploymentRegion", true, 255);
    String sector = read.string("spec", spec, "sector", false, 255);
    String promptHash = read.string("spec", spec, "promptHash", false, 128);
    RiskClass riskClass = null;
    String risk = read.string("spec", spec, "riskClass", true, 32);
    if (risk != null) {
      try {
        riskClass = RiskClass.valueOf(risk.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException unknown) {
        read.error("spec.riskClass: must be one of minimal, limited, high, prohibited");
      }
    }
    Map<String, Object> model = read.object("spec", spec, "model");
    read.onlyKeys("spec.model", model, MODEL_KEYS);
    String modelProvider = read.string("spec.model", model, "provider", false, 255);
    String modelName = read.string("spec.model", model, "name", !model.isEmpty(), 200);
    String modelVersion = read.string("spec.model", model, "version", false, 55);
    if (modelName != null && modelName.contains("@")) {
      read.error("spec.model.name: must not contain '@' (put the version in spec.model.version)");
    }
    List<String> tools = read.strings("spec", spec, "tools", 200, 128);
    for (String tool : tools) {
      if (!TOOL_NAME.matcher(tool).matches()) {
        read.error("spec.tools: '" + tool + "' may only contain letters, digits and _ . : -");
      }
    }
    if (tools.stream().distinct().count() != tools.size()) {
      read.error("spec.tools: every tool must be listed once");
    }
    return new SystemSpec(owner, purpose, riskClass, riskBasis, region, sector, modelProvider, modelName,
        modelVersion, promptHash, tools);
  }
}
