import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { obligationSignals } from "./obligation-signals";
import type { AiSystem } from "./types";

function system(overrides: Partial<AiSystem> = {}): AiSystem {
  return {
    id: "s1",
    name: "Claims triage",
    owner: "Ops",
    purpose: "Route claims",
    riskClass: "high",
    riskBasis: "Annex III",
    deploymentRegion: "EU",
    evidenceCoverage: 100,
    evalScore: 95,
    dataContractStatus: "HEALTHY",
    releaseDecision: "PASS",
    openGaps: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

const byArticle = (s: AiSystem) => Object.fromEntries(obligationSignals(s).map((o) => [o.article, o.status]));

describe("obligationSignals", () => {
  it("never marks an article as passed, even for a perfect system", () => {
    const statuses = obligationSignals(system()).map((o) => o.status as string);
    assert.ok(!statuses.includes("PASS"));
  });

  it("reports articles Devernance has no data for as not assessed", () => {
    const s = byArticle(system());
    assert.equal(s["Art. 9"], "NOT_ASSESSED");
    assert.equal(s["Art. 12"], "NOT_ASSESSED");
    assert.equal(s["Art. 13"], "NOT_ASSESSED");
    assert.equal(s["Art. 14"], "NOT_ASSESSED");
  });

  it("turns real gate inputs into gaps", () => {
    const s = byArticle(
      system({
        dataContractStatus: "BREACH",
        evidenceCoverage: 40,
        evalScore: 0,
        openGaps: ["Human oversight evidence required", "Missing evidence: MODEL_CARD"],
      }),
    );
    assert.equal(s["Art. 10"], "GAP");
    assert.equal(s["Art. 11"], "GAP");
    assert.equal(s["Art. 14"], "GAP");
    assert.equal(s["Art. 15"], "GAP");
  });

  it("shows evidence that meets the gate's own thresholds as a signal, not a pass", () => {
    const s = byArticle(system());
    assert.equal(s["Art. 10"], "SIGNAL");
    assert.equal(s["Art. 11"], "SIGNAL");
    assert.equal(s["Art. 15"], "SIGNAL");
  });

  it("asks for review between the hard block and the pass threshold", () => {
    const s = byArticle(system({ evalScore: 80, dataContractStatus: "WARNING", evidenceCoverage: 81 }));
    assert.equal(s["Art. 10"], "REVIEW");
    assert.equal(s["Art. 11"], "REVIEW");
    assert.equal(s["Art. 15"], "REVIEW");
  });

  it("lists nothing for systems that are not high-risk, since Articles 9-15 apply to high-risk systems", () => {
    assert.deepEqual(obligationSignals(system({ riskClass: "limited" })), []);
    assert.deepEqual(obligationSignals(system({ riskClass: "HIGH" as AiSystem["riskClass"] })).length, 7);
  });
});
