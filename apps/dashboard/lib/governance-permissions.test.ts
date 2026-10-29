import assert from "node:assert/strict";
import test from "node:test";

import { allowedControlStatuses, canEditConformity } from "./governance-permissions";

test("compliance and admins can set every control status", () => {
  assert.deepEqual(allowedControlStatuses("COMPLIANCE_OFFICER", "BLOCKED"), ["PASS", "REVIEW", "BLOCKED"]);
  assert.deepEqual(allowedControlStatuses("ADMIN", "REVIEW"), ["PASS", "REVIEW", "BLOCKED"]);
});

test("the engineering lead can raise a control but never pass or unblock it", () => {
  assert.deepEqual(allowedControlStatuses("AI_ENGINEERING_LEAD", "REVIEW"), ["REVIEW", "BLOCKED"]);
  assert.deepEqual(allowedControlStatuses("AI_ENGINEERING_LEAD", "PASS"), ["REVIEW", "BLOCKED"]);
  assert.deepEqual(allowedControlStatuses("AI_ENGINEERING_LEAD", "BLOCKED"), ["BLOCKED"]);
});

test("legal and auditors cannot change controls", () => {
  assert.deepEqual(allowedControlStatuses("LEGAL_COUNSEL", "REVIEW"), []);
  assert.deepEqual(allowedControlStatuses("AUDITOR", "REVIEW"), []);
  assert.deepEqual(allowedControlStatuses(undefined, "REVIEW"), []);
});

test("conformity is edited by compliance, legal and admins only", () => {
  assert.equal(canEditConformity("LEGAL_COUNSEL"), true);
  assert.equal(canEditConformity("COMPLIANCE_OFFICER"), true);
  assert.equal(canEditConformity("ADMIN"), true);
  assert.equal(canEditConformity("AI_ENGINEERING_LEAD"), false);
  assert.equal(canEditConformity("AUDITOR"), false);
});
