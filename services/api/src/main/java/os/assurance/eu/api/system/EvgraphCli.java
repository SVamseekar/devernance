package os.assurance.eu.api.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs the evgraph CLI on accepted artifact files. The library stays outside the JVM.
 * Production images bake the pinned CLI and set {@code EVGRAPH_BIN}. Runtime install is
 * only for tests and local H2 when {@code assurance.evgraph.allow-runtime-install=true}.
 */
@Component
public class EvgraphCli {
  static final String PINNED_VERSION = "0.1.3";
  static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
  private static final String PINNED_PACKAGE = "evgraph-cli==" + PINNED_VERSION;
  private static final int MAX_CAPTURE_BYTES = 2 * 1024 * 1024;

  static final Duration BUSY_WAIT = Duration.ofSeconds(20);

  private final ObjectMapper objectMapper;
  private final String command;
  private final Duration timeout;
  private final Semaphore running;

  @Autowired
  public EvgraphCli(
      ObjectMapper objectMapper,
      @Value("${assurance.evgraph.command:evgraph}") String command,
      @Value("${assurance.evgraph.allow-runtime-install:false}") boolean allowRuntimeInstall,
      @Value("${assurance.evgraph.max-concurrent-scans:4}") int maxConcurrentScans) {
    this(objectMapper, resolveCommand(command, allowRuntimeInstall), allowRuntimeInstall, DEFAULT_TIMEOUT,
        maxConcurrentScans);
  }

  EvgraphCli(ObjectMapper objectMapper, String resolvedCommand, boolean allowRuntimeInstall, Duration timeout) {
    this(objectMapper, resolvedCommand, allowRuntimeInstall, timeout, 4);
  }

  EvgraphCli(ObjectMapper objectMapper, String resolvedCommand, boolean allowRuntimeInstall, Duration timeout,
      int maxConcurrentScans) {
    this.objectMapper = objectMapper;
    this.command = resolvedCommand;
    this.timeout = timeout;
    this.running = new Semaphore(Math.max(1, maxConcurrentScans), true);
  }

  public List<Map<String, Object>> currentGaps(Map<String, Object> artifacts) {
    // Each scan starts Python processes; cap how many run at once so a burst of exports cannot exhaust the host.
    boolean acquired;
    try {
      acquired = running.tryAcquire(BUSY_WAIT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence scan interrupted; try again.");
    }
    if (!acquired) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Evidence scans are busy; try again in a minute.");
    }
    try {
      return scan(artifacts);
    } finally {
      running.release();
    }
  }

  private List<Map<String, Object>> scan(Map<String, Object> artifacts) {
    Path dir = null;
    try {
      dir = Files.createTempDirectory("evgraph-pack");
      Path modelCard = dir.resolve("model_card.json");
      Path approval = dir.resolve("approval.json");
      Path deployment = dir.resolve("deployment.json");
      Path dataset = dir.resolve("dataset_manifest.csv");
      objectMapper.writeValue(modelCard.toFile(), artifacts.get("model_card"));
      objectMapper.writeValue(approval.toFile(), artifacts.get("approval"));
      objectMapper.writeValue(deployment.toFile(), artifacts.get("deployment"));
      Files.writeString(dataset, String.valueOf(artifacts.get("dataset_manifest_csv")));
      List<Map<String, Object>> gaps = new ArrayList<>();
      gaps.addAll(gapsFrom(command, "scan", modelCard.toString(), approval.toString(), deployment.toString()));
      gaps.addAll(gapsFrom(command, "scan-dataset-manifest", dataset.toString()));
      return gaps;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("evgraph scan failed", e);
    } finally {
      deleteRecursively(dir);
    }
  }

  static String resolveCommand(String configured, boolean allowRuntimeInstall) {
    String override = System.getenv("EVGRAPH_BIN");
    if (executable(override)) {
      return override;
    }
    String fallback = configured == null || configured.isBlank() ? "evgraph" : configured;
    if (onPath(fallback)) {
      return fallback;
    }
    if (allowRuntimeInstall && "evgraph".equals(fallback)) {
      return pinnedCli().toString();
    }
    throw new IllegalStateException("evgraph CLI not found. Set EVGRAPH_BIN or install evgraph-cli==" + PINNED_VERSION);
  }

  static Path installPinned(Path root) throws IOException, InterruptedException {
    Path bin = root.resolve("bin/evgraph");
    if (Files.isExecutable(bin)) {
      return bin;
    }
    synchronized (EvgraphCli.class) {
      if (Files.isExecutable(bin)) {
        return bin;
      }
      Files.createDirectories(root.getParent() == null ? Path.of(".") : root.getParent());
      run("python3", "-m", "venv", root.toString());
      run(root.resolve("bin/pip").toString(), "install", "--disable-pip-version-check", PINNED_PACKAGE);
    }
    if (!Files.isExecutable(bin)) {
      throw new IOException("pinned evgraph " + PINNED_VERSION + " did not install " + bin);
    }
    return bin;
  }

  private static Path pinnedCli() {
    Path root = Path.of(System.getProperty("user.dir", ".")).resolve("target/evgraph-" + PINNED_VERSION);
    try {
      return installPinned(root);
    } catch (Exception e) {
      throw new IllegalStateException("evgraph " + PINNED_VERSION + " is not installed", e);
    }
  }

  private static boolean executable(String path) {
    return path != null && !path.isBlank() && Files.isExecutable(Path.of(path));
  }

  private static boolean onPath(String command) {
    if (command.contains("/") || command.contains("\\")) {
      return Files.isExecutable(Path.of(command));
    }
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) {
      return false;
    }
    for (String dir : path.split(java.io.File.pathSeparator)) {
      if (Files.isExecutable(Path.of(dir).resolve(command))) {
        return true;
      }
    }
    return false;
  }

  private static void run(String... argv) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int code = process.waitFor();
    if (code != 0) {
      throw new IOException(output.isBlank() ? "exit " + code : output.trim());
    }
  }

  private List<Map<String, Object>> gapsFrom(String command, String subcommand, String... paths) throws Exception {
    List<String> argv = new ArrayList<>();
    argv.add(command);
    argv.add(subcommand);
    argv.addAll(List.of(paths));
    argv.add("--format");
    argv.add("json");
    ProcessBuilder builder = new ProcessBuilder(argv);
    Path errFile = Files.createTempFile("evgraph-stderr", ".log");
    builder.redirectError(errFile.toFile());
    Process process = builder.start();
    CompletableFuture<byte[]> stdoutFuture = CompletableFuture.supplyAsync(() -> {
      try (var in = process.getInputStream()) {
        return in.readNBytes(MAX_CAPTURE_BYTES);
      } catch (IOException e) {
        return new byte[0];
      }
    });
    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new IllegalStateException("evgraph " + subcommand + " timed out after " + timeout.toSeconds() + "s");
      }
      String stdout = new String(stdoutFuture.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        String stderr = Files.readString(errFile).lines().limit(20).collect(Collectors.joining("\n"));
        throw new IllegalStateException(stderr.isBlank() ? "evgraph exit " + process.exitValue() : stderr);
      }
      JsonNode findings = objectMapper.readTree(stdout).get("findings");
      List<Map<String, Object>> gaps = new ArrayList<>();
      if (findings == null || !findings.isArray()) {
        return gaps;
      }
      for (JsonNode finding : findings) {
        String outcome = finding.path("outcome").asText();
        if ("EXPECTATION_MET".equals(outcome)) {
          continue;
        }
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("rule_id", finding.path("rule_id").asText());
        gap.put("outcome", outcome);
        gaps.add(gap);
      }
      return gaps;
    } finally {
      Files.deleteIfExists(errFile);
    }
  }

  private static void deleteRecursively(Path dir) {
    if (dir == null) {
      return;
    }
    try (var paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
          // best effort
        }
      });
    } catch (IOException ignored) {
      // best effort
    }
  }
}
