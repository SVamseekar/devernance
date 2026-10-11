import assert from "node:assert/strict";
import test from "node:test";
import { SYSTEM_EXPORTS, downloadFilename, exportPath } from "./export-view";

test("the filename comes from the Content-Disposition header", () => {
  assert.equal(
    downloadFilename('attachment; filename="grc-export-abc-20261011T090000Z.zip"', "fallback.zip"),
    "grc-export-abc-20261011T090000Z.zip",
  );
  assert.equal(downloadFilename("attachment; filename=plain.csv", "fallback.csv"), "plain.csv");
});

test("a missing or empty header falls back to the given name", () => {
  assert.equal(downloadFilename(null, "fallback.zip"), "fallback.zip");
  assert.equal(downloadFilename("attachment", "fallback.zip"), "fallback.zip");
});

test("a filename with a path in it is reduced to its last segment", () => {
  assert.equal(downloadFilename('attachment; filename="../../etc/passwd"', "fallback.zip"), "passwd");
});

test("each export points at its API route and never promises a verdict", () => {
  assert.deepEqual(
    SYSTEM_EXPORTS.map((e) => exportPath("sys-1", e.kind)),
    ["/systems/sys-1/questionnaire-export?format=csv", "/systems/sys-1/grc-export"],
  );
  for (const e of SYSTEM_EXPORTS) {
    assert.doesNotMatch(`${e.label} ${e.description}`, /compliant|certified|guarantee|conformity/i);
    assert.match(e.upgradeText, /Business/);
  }
});
