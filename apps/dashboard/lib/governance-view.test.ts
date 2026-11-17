import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { attentionOrder, canAnswer, decisionOutcome, describeFacts } from "./governance-view";
import type { GovernanceDecision, GovernanceSystemStatus } from "./types";

function decision(partial: Partial<GovernanceDecision>): GovernanceDecision {
  return {
    id: "d", system: "s", systemId: "x", systemVersion: 1, action: "payment.refund", resource: {}, context: {},
    model: null, environment: "production", agent: "claims-agent", status: "ALLOW", enforced: true,
    authorized: true, rule: "payment.refund", reason: "", policy: null, approval: null, execution: null,
    createdAt: "2026-11-17T00:00:00Z", ...partial,
  };
}

function system(name: string, partial: Partial<GovernanceSystemStatus>): GovernanceSystemStatus {
  return {
    system: name, systemId: name, systemVersion: 1, model: null, tools: [], policy: null,
    runtimeEnforcement: "enforce", validation: { valid: true, errors: 0, warnings: 0, findings: [] }, drift: [],
    pendingApprovals: 0, decisionsLast24h: { ALLOW: 0, DENY: 0, REVIEW: 0 }, ...partial,
  };
}

describe("governance view", () => {
  it("says when an allowed action was never reported, and when a denial blocked", () => {
    assert.equal(decisionOutcome(decision({})), "outcome not reported");
    assert.equal(
      decisionOutcome(decision({ execution: { status: "EXECUTED", detail: null, reference: null, recordedAt: "" } })),
      "executed");
    assert.equal(decisionOutcome(decision({ status: "DENY", authorized: false })), "blocked");
    assert.equal(decisionOutcome(decision({ status: "DENY", enforced: false })), "monitor only");
  });

  it("flattens request facts and shortens long values", () => {
    assert.equal(describeFacts({ customer_id: "CUST-102" }, { amount: 75000 }), "customer_id: CUST-102 · amount: 75000");
    assert.ok(describeFacts({ note: "x".repeat(100) }).endsWith("..."));
  });

  it("puts broken and drifted systems first", () => {
    const drifted = system("b", { drift: [{ inSync: false } as GovernanceSystemStatus["drift"][number]] });
    const broken = system("c", { validation: { valid: false, errors: 1, warnings: 0, findings: [] } });
    const fine = system("a", {});
    assert.deepEqual(attentionOrder([fine, drifted, broken]).map((s) => s.system), ["c", "b", "a"]);
  });

  it("lets only the roles the policy names answer an approval", () => {
    assert.equal(canAnswer("COMPLIANCE_OFFICER", ["COMPLIANCE_OFFICER"]), true);
    assert.equal(canAnswer("ADMIN", ["COMPLIANCE_OFFICER"]), false);
    assert.equal(canAnswer(undefined, ["ADMIN"]), false);
  });
});
