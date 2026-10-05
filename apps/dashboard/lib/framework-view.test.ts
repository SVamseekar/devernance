import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { groupByFunction, statusLabel, FRAMEWORK_UPGRADE_TEXT } from "./framework-view";
import type { FrameworkItemStatus } from "./types";

const item = (id: string, status: FrameworkItemStatus["status"] = "no_evidence"): FrameworkItemStatus => ({
  id,
  title: id,
  status,
  controls: [],
  evidence: [],
  requiredControls: ["RISK_MANAGEMENT"],
  requiredEvidence: ["POLICY"],
});

describe("groupByFunction", () => {
  it("groups NIST categories by their function in GOVERN, MAP, MEASURE, MANAGE order", () => {
    const groups = groupByFunction([item("MANAGE 1"), item("GOVERN 2"), item("MAP 1"), item("MEASURE 4"), item("GOVERN 1")]);
    assert.deepEqual(groups.map((g) => g.name), ["GOVERN", "MAP", "MEASURE", "MANAGE"]);
    assert.deepEqual(groups[0].items.map((i) => i.id), ["GOVERN 2", "GOVERN 1"]);
  });

  it("keeps ids without a NIST function prefix in their own group, in input order", () => {
    const groups = groupByFunction([item("SB26-189 §6-1-1702"), item("A.2.2")]);
    assert.deepEqual(groups.map((g) => g.name), ["SB26-189", "A.2.2"]);
  });

  it("counts covered items per group", () => {
    const groups = groupByFunction([item("MAP 1", "covered"), item("MAP 2", "partial"), item("MAP 3")]);
    assert.equal(groups[0].covered, 1);
    assert.equal(groups[0].items.length, 3);
  });
});

describe("statusLabel", () => {
  it("says 'No evidence yet' instead of an empty state", () => {
    assert.equal(statusLabel("no_evidence"), "No evidence yet");
    assert.equal(statusLabel("partial"), "Partial");
    assert.equal(statusLabel("covered"), "Evidence linked");
  });
});

describe("upgrade text", () => {
  it("names the plan that includes the crosswalk", () => {
    assert.equal(FRAMEWORK_UPGRADE_TEXT, "Available on Team and above");
  });
});
