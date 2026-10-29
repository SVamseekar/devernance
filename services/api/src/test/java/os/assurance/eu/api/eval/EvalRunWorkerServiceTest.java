package os.assurance.eu.api.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.audit.AuditService;
import os.assurance.eu.api.system.ReleaseDecision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class EvalRunWorkerServiceTest {

  private final EvalRunRepository evalRuns = mock(EvalRunRepository.class);
  private final EvalRunWorkerService workerService = new EvalRunWorkerService(
      mock(EvalDatasetRepository.class),
      evalRuns,
      mock(EvalRunCompletionService.class),
      mock(AuditService.class),
      mock(EvalRunMetrics.class),
      true);

  @Test
  void syntheticMetricsAreRefusedUnlessEnabled() {
    EvalRunWorkerService off = new EvalRunWorkerService(
        mock(EvalDatasetRepository.class), evalRuns, mock(EvalRunCompletionService.class),
        mock(AuditService.class), mock(EvalRunMetrics.class), false);

    ResponseStatusException refused = assertThrows(ResponseStatusException.class, () -> off.execute(UUID.randomUUID()));

    assertThat(refused.getStatusCode().value()).isEqualTo(409);
    assertThat(refused.getReason()).contains("signed");
  }

  @Test
  void callbackVerifierFailsFastWhenSecretIsMissing() {
    EvalCallbackSignatureVerifier verifier = new EvalCallbackSignatureVerifier(
        "",
        300,
        Clock.systemUTC(),
        mock(EvalRunMetrics.class));

    assertThrows(IllegalStateException.class, verifier::validateConfiguration);
  }

  @Test
  void retryableFailureReturnsRunToQueueWithBackoffMetadata() {
    when(evalRuns.save(any(EvalRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    EvalRun running = runningRun(1, 3);

    EvalRun failed = workerService.recordFailure(running, new IllegalStateException("Transient worker error"));

    assertThat(failed.status()).isEqualTo("queued");
    assertThat(failed.workerAttempts()).isEqualTo(1);
    assertThat(failed.maxAttempts()).isEqualTo(3);
    assertThat(failed.queuedAt()).isAfter(failed.failedAt());
    assertThat(failed.startedAt()).isEqualTo(running.startedAt());
    assertThat(failed.completedAt()).isNull();
    assertThat(failed.failureReason()).isEqualTo("Transient worker error");
  }

  @Test
  void exhaustedFailureBecomesTerminalAndKeepsOriginalQueueTime() {
    when(evalRuns.save(any(EvalRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    EvalRun running = runningRun(3, 3);

    EvalRun failed = workerService.recordFailure(running, new RuntimeException());

    assertThat(failed.status()).isEqualTo("failed");
    assertThat(failed.workerAttempts()).isEqualTo(3);
    assertThat(failed.queuedAt()).isEqualTo(running.queuedAt());
    assertThat(failed.failedAt()).isNotNull();
    assertThat(failed.failureReason()).isEqualTo("RuntimeException");
  }

  @Test
  void legacyQueuedRunWithoutDatasetIdBecomesTerminalFailure() {
    UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000201");
    when(evalRuns.claimQueuedForExecution(runId)).thenReturn(Optional.of(new EvalRunClaim(claimedRunWithoutDataset(), true)));
    when(evalRuns.save(any(EvalRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

    assertThrows(ResponseStatusException.class, () -> workerService.execute(runId));

    ArgumentCaptor<EvalRun> savedRun = ArgumentCaptor.forClass(EvalRun.class);
    verify(evalRuns).save(savedRun.capture());
    EvalRun failed = savedRun.getValue();
    assertThat(failed.status()).isEqualTo("failed");
    assertThat(failed.datasetId()).isNull();
    assertThat(failed.workerAttempts()).isEqualTo(1);
    assertThat(failed.startedAt()).isNotNull();
    assertThat(failed.failedAt()).isNotNull();
    assertThat(failed.failureReason()).isEqualTo("Eval dataset is not registered");
  }

  private EvalRun runningRun(int workerAttempts, int maxAttempts) {
    Instant createdAt = Instant.parse("2026-06-05T10:00:00Z");
    Instant queuedAt = Instant.parse("2026-06-05T10:01:00Z");
    Instant startedAt = Instant.parse("2026-06-05T10:02:00Z");
    return new EvalRun(
        UUID.fromString("00000000-0000-0000-0000-000000000201"),
        UUID.fromString("00000000-0000-0000-0000-000000000301"),
        UUID.fromString("00000000-0000-0000-0000-000000000401"),
        "running",
        "golden-eu-claims-v4",
        "claims-triage-worker",
        "claims-routing-worker",
        0.85,
        Map.of(),
        ReleaseDecision.REVIEW,
        createdAt,
        queuedAt,
        startedAt,
        null,
        null,
        workerAttempts,
        maxAttempts,
        null);
  }

  private EvalRun claimedRunWithoutDataset() {
    Instant createdAt = Instant.parse("2026-06-05T10:00:00Z");
    Instant queuedAt = Instant.parse("2026-06-05T10:01:00Z");
    return new EvalRun(
        UUID.fromString("00000000-0000-0000-0000-000000000201"),
        UUID.fromString("00000000-0000-0000-0000-000000000301"),
        null,
        "running",
        "legacy-dataset-name",
        "claims-triage-worker",
        "claims-routing-worker",
        0.85,
        Map.of(),
        ReleaseDecision.REVIEW,
        createdAt,
        queuedAt,
        Instant.parse("2026-06-05T10:02:00Z"),
        null,
        null,
        1,
        3,
        null);
  }
}
