package os.assurance.eu.api.publicclaims;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import os.assurance.eu.api.publicclaims.PublicClaimsCatalog.PublicClaimsSystemSpec;
import os.assurance.eu.api.system.AiSystem;
import os.assurance.eu.api.system.AiSystemRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PublicClaimsService {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final ObjectMapper objectMapper;
  private final PublicClaimsProperties properties;
  private final AiSystemRepository systems;
  private final PublicClaimsCatalog catalog;

  public PublicClaimsService(
      ObjectMapper objectMapper,
      PublicClaimsProperties properties,
      AiSystemRepository systems) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.systems = systems;
    this.catalog = loadCatalog(objectMapper);
  }

  public PublicClaimsCatalog catalog() {
    return catalog;
  }

  public boolean seedingEnabled() {
    return properties.isPublicClaims();
  }

  public PublicClaimsViews.Index index() {
    Map<String, AiSystem> byModel = indexRegistered();
    List<PublicClaimsViews.Teaser> teasers = new ArrayList<>();
    for (PublicClaimsSystemSpec spec : catalog.systems()) {
      teasers.add(toTeaser(spec, byModel.get(spec.modelName())));
    }
    return new PublicClaimsViews.Index(
        catalog.disclaimer(),
        catalog.retrievedAt(),
        properties.isPublicClaims(),
        catalog.library(),
        catalog.howtoPromotion(),
        catalog.howtoDataset(),
        teasers);
  }

  public PublicClaimsViews.Teaser teaser(String slug) {
    PublicClaimsSystemSpec spec = catalog.require(slug);
    return toTeaser(spec, indexRegistered().get(spec.modelName()));
  }

  public PublicClaimsViews.Artifacts artifacts(String slug) {
    catalog.require(slug);
    return new PublicClaimsViews.Artifacts(
        slug,
        catalog.disclaimer(),
        catalog.library(),
        catalog.howtoPromotion(),
        catalog.howtoDataset(),
        readJson(slug, "model_card.json"),
        readJson(slug, "approval.json"),
        readJson(slug, "deployment.json"),
        readText(slug, "dataset_manifest.csv"));
  }

  public Map<String, Object> evgraphArtifacts(String slug) {
    PublicClaimsViews.Artifacts files = artifacts(slug);
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("howto", files.howtoPromotion() + " ; " + files.howtoDataset());
    root.put("howtoPromotion", files.howtoPromotion());
    root.put("howtoDataset", files.howtoDataset());
    root.put("library", files.library());
    root.put("disclaimer", files.disclaimer());
    root.put("slug", slug);
    root.put("model_card", files.model_card());
    root.put("approval", files.approval());
    root.put("deployment", files.deployment());
    root.put("dataset_manifest_csv", files.dataset_manifest_csv());
    return root;
  }

  public String evidenceBody(PublicClaimsSystemSpec spec) {
    StringBuilder body = new StringBuilder();
    body.append("# Public claims reconstruction — ").append(spec.legalName()).append("\n\n");
    body.append(catalog.disclaimer()).append("\n\n");
    body.append("Retrieved ").append(catalog.retrievedAt()).append(". ");
    body.append("HQ: ").append(spec.hq()).append(".\n\n");
    for (var source : spec.sources()) {
      body.append("## ").append(source.title()).append("\n");
      body.append("URL: ").append(source.url()).append("\n");
      body.append("Retrieved: ").append(source.retrievedAt()).append("\n\n");
      body.append("> ").append(source.quote()).append("\n\n");
    }
    return body.toString();
  }

  public String firstSourceUri(PublicClaimsSystemSpec spec) {
    if (spec.sources().isEmpty() || spec.sources().get(0).url() == null) {
      return "https://devernance.space/disclaimer";
    }
    return spec.sources().get(0).url();
  }

  private PublicClaimsViews.Teaser toTeaser(PublicClaimsSystemSpec spec, AiSystem registered) {
    return new PublicClaimsViews.Teaser(
        spec.slug(),
        spec.legalName(),
        spec.hq(),
        spec.systemName(),
        spec.purpose(),
        spec.riskClass() == null ? null : spec.riskClass().name(),
        spec.riskBasis(),
        spec.sector(),
        spec.vendorName(),
        spec.modelName(),
        registered == null ? null : registered.id(),
        registered == null || registered.releaseDecision() == null
            ? null
            : registered.releaseDecision().name(),
        spec.sources(),
        spec.openGaps());
  }

  private Map<String, AiSystem> indexRegistered() {
    Map<String, AiSystem> byModel = new LinkedHashMap<>();
    try {
      for (AiSystem system : systems.findAll()) {
        if (system.modelName() != null && !system.modelName().isBlank()) {
          byModel.putIfAbsent(system.modelName(), system);
        }
      }
    } catch (IllegalStateException ignored) {
      // Tenant context is required; catalog endpoints still work with files only.
    }
    return byModel;
  }

  private Map<String, Object> readJson(String slug, String filename) {
    try (InputStream in = resource(slug, filename).getInputStream()) {
      Map<String, Object> parsed = objectMapper.readValue(in, MAP_TYPE);
      return parsed == null ? Map.of() : parsed;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read public-claims " + slug + "/" + filename, ex);
    }
  }

  private String readText(String slug, String filename) {
    try (InputStream in = resource(slug, filename).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read public-claims " + slug + "/" + filename, ex);
    }
  }

  private ClassPathResource resource(String slug, String filename) {
    return new ClassPathResource(PublicClaimsCatalog.CLASSPATH_ROOT + "/" + slug + "/" + filename);
  }

  private static PublicClaimsCatalog loadCatalog(ObjectMapper objectMapper) {
    ClassPathResource resource =
        new ClassPathResource(PublicClaimsCatalog.CLASSPATH_ROOT + "/catalog.json");
    try (InputStream in = resource.getInputStream()) {
      PublicClaimsCatalog loaded = objectMapper.readValue(in, PublicClaimsCatalog.class);
      if (loaded == null || loaded.systems().isEmpty()) {
        throw new IllegalStateException("public-claims catalog.json is empty");
      }
      return loaded;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load public-claims/catalog.json", ex);
    }
  }
}
