import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { firstPendingStage, isActionableStage, workflowsForSystem } from "./workflow-helpers";
import type { ApprovalWorkflow } from "./types";

const wf = (statuses: string[]) =>
  ({
    id: "wf-1",
    systemId: "sys-1",
    status: "OPEN",
    stages: statuses.map((status, i) => ({
      id: `st-${i}`,
      status,
      requiredRole: "COMPLIANCE_OFFICER",
      stageOrder: i,
    })),
  }) as unknown as ApprovalWorkflow;

describe("firstPendingStage", () => {
  it("returns the first stage that is not approved or overridden", () => {
    assert.equal(firstPendingStage(wf(["APPROVED", "PENDING", "PENDING"]))?.id, "st-1");
  });
  it("returns null when every stage is complete", () => {
    assert.equal(firstPendingStage(wf(["APPROVED", "OVERRIDDEN"])), null);
  });
});

describe("isActionableStage", () => {
  const stages = wf(["APPROVED", "PENDING", "PENDING"]).stages;

  it("lets the signed-in user's real role act on its own stage when it is next", () => {
    assert.equal(isActionableStage(stages[1], stages, "COMPLIANCE_OFFICER"), true);
  });
  it("lets an admin act on the next stage, as the API does", () => {
    assert.equal(isActionableStage(stages[1], stages, "ADMIN"), true);
  });
  it("refuses other roles, later stages, and an unknown role", () => {
    assert.equal(isActionableStage(stages[1], stages, "AI_ENGINEERING_LEAD"), false);
    assert.equal(isActionableStage(stages[2], stages, "COMPLIANCE_OFFICER"), false);
    assert.equal(isActionableStage(stages[1], stages, undefined), false);
  });
  it("ignores the old demo persona ids", () => {
    assert.equal(isActionableStage(stages[1], stages, "actor-priya"), false);
  });
});

describe("workflowsForSystem", () => {
  const open = { ...wf(["PENDING"]), systemId: "11111111-1111-4111-8111-111111111111" } as ApprovalWorkflow;

  it("uses the API's open workflows for a real system", () => {
    assert.deepEqual(workflowsForSystem("11111111-1111-4111-8111-111111111111", [open], {}, false), [open]);
    assert.deepEqual(workflowsForSystem("22222222-2222-4222-8222-222222222222", [open], {}, false), []);
  });
  it("never shows sample workflows unless mock fallback is on", () => {
    const sample = { "mock-sys-001": [open] };
    assert.deepEqual(workflowsForSystem("mock-sys-001", [], sample, false), []);
    assert.deepEqual(workflowsForSystem("mock-sys-001", [], sample, true), [open]);
  });
});
