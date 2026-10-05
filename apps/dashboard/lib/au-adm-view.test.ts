import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { admRows, admSummary, AU_ADM_UPGRADE_TEXT, fieldLabel } from "./au-adm-view";
import type { AuAdmSystem } from "./types";

const sys = (name: string, status: AuAdmSystem["status"], missing: string[] = []): AuAdmSystem => ({
  systemId: name,
  name,
  status,
  missing,
  reason: "",
  draftStatement: status === "include" ? `Draft for review by your privacy counsel: We use ${name}.` : null,
});

describe("admRows", () => {
  it("lists systems needing input first, then included, then not applicable", () => {
    const rows = admRows([sys("C", "not_applicable"), sys("A", "include"), sys("B", "needs_input", ["decisionImpact"])]);
    assert.deepEqual(rows.map((r) => r.name), ["B", "A", "C"]);
  });

  it("never offers a draft to copy for a system that needs input", () => {
    const [row] = admRows([sys("B", "needs_input", ["decisionImpact", "dataSources"])]);
    assert.equal(row.copyText, null);
    assert.equal(row.detail, "Missing: decision impact, data sources");
  });
});

describe("admSummary", () => {
  it("counts each status", () => {
    const s = admSummary([sys("A", "include"), sys("B", "needs_input", ["purpose"]), sys("C", "include")]);
    assert.deepEqual(s, { include: 2, needsInput: 1, notApplicable: 0 });
  });
});

describe("labels", () => {
  it("names the plan that includes the pack and labels fields in plain words", () => {
    assert.equal(AU_ADM_UPGRADE_TEXT, "Available on Business and above");
    assert.equal(fieldLabel("affectedUsers"), "affected people");
  });
});
