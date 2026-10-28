import assert from "node:assert/strict";
import test from "node:test";

import { evalCallbackSnippet, evalRunLines } from "./eval-callback";
import type { EvalRun } from "./types";

const run = (over: Partial<EvalRun>): EvalRun => ({
  runId: "r1", systemId: "s1", datasetId: "d1", status: "completed", dataset: "golden-v1",
  modelVersion: "gpt-4o-mini-2024-07-18", promptVersion: "v12", threshold: 0.85,
  metrics: { faithfulness: 0.91, relevance: 0.89, safetyRefusal: 0.97, biasSlicePassRate: 0.6 },
  releaseDecision: "BLOCKED", createdAt: "", queuedAt: "", startedAt: null, completedAt: "2026-10-28T09:00:00Z",
  failedAt: null, workerAttempts: 0, maxAttempts: 3, failureReason: null, ...over,
});

test("the callback snippet signs timestamp.body and targets the run", () => {
  const snippet = evalCallbackSnippet("abc");
  assert.match(snippet, /RUN_ID=abc/);
  assert.match(snippet, /"\$ASSURANCE_URL\/api\/v1\/eval-runs\/\$RUN_ID\/result"/);
  assert.match(snippet, /printf '%s\.%s' "\$TS" "\$BODY"/);
  assert.match(snippet, /X-Eval-Signature: v1=\$SIG/);
});

test("a queued run shows no numbers", () => {
  const lines = evalRunLines(run({ status: "queued", metrics: {} as EvalRun["metrics"] }));
  assert.equal(lines.some((l) => l.includes("%")), false);
  assert.match(lines[1], /waiting for your eval harness/);
});

test("a completed run shows versions, metrics and its own result", () => {
  const lines = evalRunLines(run({}));
  assert.ok(lines.includes("> bias slice pass:    60%"));
  assert.ok(lines.some((l) => l.includes("prompt v12")));
  assert.ok(lines.includes("> run result:         BLOCKED"));
});

test("synthetic metrics are labelled", () => {
  const lines = evalRunLines(run({ metrics: { faithfulness: 0.9, synthetic: true } as unknown as EvalRun["metrics"] }));
  assert.ok(lines.some((l) => l.includes("SYNTHETIC")));
});
