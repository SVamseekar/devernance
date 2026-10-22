package os.assurance.eu.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvgraphCliSafetyTest {
  private Path fakeBinary(String body) throws Exception {
    Path script = Files.createTempFile("fake-evgraph", ".sh");
    Files.writeString(script, "#!/bin/sh\n" + body + "\n");
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    return script;
  }

  private Map<String, Object> artifacts() {
    return Map.of("model_card", Map.of("model_name", "m"), "approval", Map.of(), "deployment", Map.of(),
        "dataset_manifest_csv", "name,license\nd,MIT\n");
  }

  @Test
  void hangingBinaryTimesOutAndCleansUp() throws Exception {
    Path bin = fakeBinary("sleep 30");
    EvgraphCli cli = new EvgraphCli(new ObjectMapper(), bin.toString(), false, Duration.ofSeconds(1));
    long before = countTempDirs();
    assertThatThrownBy(() -> cli.currentGaps(artifacts()))
        .hasMessageContaining("timed out");
    assertThat(countTempDirs()).isEqualTo(before);
  }

  @Test
  void hugeStderrDoesNotDeadlock() throws Exception {
    Path bin = fakeBinary("head -c 5000000 /dev/zero | tr '\\0' 'x' 1>&2; echo '{\"findings\":[]}'");
    EvgraphCli cli = new EvgraphCli(new ObjectMapper(), bin.toString(), false, Duration.ofSeconds(20));
    assertThat(cli.currentGaps(artifacts())).isEmpty();
  }

  @Test
  void noDatasetManifestMeansNoDatasetScan() throws Exception {
    Path calls = Files.createTempFile("evgraph-calls", ".log");
    Path bin = fakeBinary("echo \"$1\" >> " + calls + "; echo '{\"findings\":[]}'");
    EvgraphCli cli = new EvgraphCli(new ObjectMapper(), bin.toString(), false, Duration.ofSeconds(20));
    Map<String, Object> withoutDataset = new java.util.HashMap<>(artifacts());
    withoutDataset.remove("dataset_manifest_csv");
    cli.currentGaps(withoutDataset);
    assertThat(Files.readAllLines(calls)).containsExactly("scan");
  }

  private long countTempDirs() throws Exception {
    try (var s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return s.filter(p -> p.getFileName().toString().startsWith("evgraph-pack")).count();
    }
  }
}
