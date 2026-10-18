import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { evidenceTypesFor, firstDutyDates, releaseGateWorkflow } from "./onboarding";
import type { CorpusProvision } from "./types";

const provision = (over: Partial<CorpusProvision>): CorpusProvision => ({
  provisionKey: "k",
  article: null,
  paragraph: null,
  point: null,
  annex: null,
  textExcerpt: "",
  forceStatus: "FUTURE",
  forceFrom: "2027-12-02",
  scopeNote: null,
  ...over,
});

describe("evidenceTypesFor", () => {
  it("lists the full evidence set for high risk", () => {
    assert.deepEqual(evidenceTypesFor("high"), ["DPIA", "Model card", "Policy", "Control map", "Vendor documentation"]);
  });
  it("asks for less as risk falls", () => {
    assert.ok(evidenceTypesFor("limited").length < evidenceTypesFor("high").length);
    assert.ok(evidenceTypesFor("minimal").length <= evidenceTypesFor("limited").length);
  });
});

describe("firstDutyDates", () => {
  it("picks the Article 50 and Annex III force dates from the corpus", () => {
    const dates = firstDutyDates([
      provision({ article: "50", forceFrom: "2026-08-02", forceStatus: "IN_FORCE" }),
      provision({ annex: "III", forceFrom: "2027-12-02" }),
      provision({ article: "6", forceFrom: "2026-01-01" }),
    ]);
    assert.deepEqual(dates, {
      article50: { date: "2026-08-02", status: "IN_FORCE" },
      annexIII: { date: "2027-12-02", status: "FUTURE" },
    });
  });
  it("returns nulls when the corpus has neither", () => {
    assert.deepEqual(firstDutyDates([]), { article50: null, annexIII: null });
  });
});

describe("releaseGateWorkflow", () => {
  it("fills in the system id and keeps the key in a secret", () => {
    const yaml = releaseGateWorkflow("sys-123");
    assert.match(yaml, /systemId=sys-123/);
    assert.match(yaml, /secrets\.ASSURANCE_API_KEY/);
    assert.doesNotMatch(yaml, /(dvn|aos)_/);
  });
  it("leaves a placeholder when there is no system yet", () => {
    assert.match(releaseGateWorkflow(), /<SYSTEM_ID>/);
  });
});
