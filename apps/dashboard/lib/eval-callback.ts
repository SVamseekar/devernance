import type { EvalRun } from "./types";

/**
 * Devernance never measures a model itself. A queued run waits for your eval harness to report what it measured
 * through the HMAC-signed result callback; this is the call the harness makes. Host-neutral like the gate
 * snippets: ASSURANCE_URL, TOKEN and EVAL_CALLBACK_SECRET come from the CI environment.
 */
export function evalCallbackSnippet(runId: string): string {
  return [
    `RUN_ID=${runId}`,
    `BODY='{"metrics":{"faithfulness":0.91,"relevance":0.89,"safetyRefusal":0.97,"biasSlicePassRate":0.88}}'`,
    "TS=$(date +%s)",
    'SIG=$(printf \'%s.%s\' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$EVAL_CALLBACK_SECRET" | sed \'s/^.* //\')',
    'curl -X PATCH "$ASSURANCE_URL/api/v1/eval-runs/$RUN_ID/result" \\',
    '  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \\',
    '  -H "X-Eval-Timestamp: $TS" -H "X-Eval-Signature: v1=$SIG" -d "$BODY"',
  ].join("\n");
}

const pct = (value: number | undefined) => (value === undefined ? "—" : `${Math.round(value * 100)}%`);

/** Console lines for a run; a queued run says what it is waiting for instead of showing numbers. */
export function evalRunLines(run: EvalRun): string[] {
  if (run.status !== "completed") {
    return [
      `> run ${run.runId} — ${run.status}`,
      "> waiting for your eval harness to report measured results (signed callback below)",
    ];
  }
  const m = run.metrics;
  const lines = [
    `> run ${run.runId} — completed ${run.completedAt ?? ""}`.trim(),
    `> model ${run.modelVersion} · prompt ${run.promptVersion} · dataset ${run.dataset}`,
    `> faithfulness:       ${pct(m.faithfulness)}`,
    `> relevance:          ${pct(m.relevance)}`,
    `> safety refusal:     ${pct(m.safetyRefusal)}`,
    `> bias slice pass:    ${pct(m.biasSlicePassRate)}`,
    `> run result:         ${run.releaseDecision}`,
  ];
  if ((m as Record<string, unknown>).synthetic === true) {
    lines.push("> SYNTHETIC: placeholder metrics from a demo worker, not a measurement");
  }
  return lines;
}
