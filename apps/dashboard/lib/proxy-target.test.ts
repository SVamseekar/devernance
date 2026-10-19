import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { proxyTimeoutMs, proxyUpstreamPath } from "./proxy-target";

describe("proxyUpstreamPath", () => {
  it("joins ordinary segments", () => {
    assert.equal(proxyUpstreamPath(["systems", "abc", "release-gate"]), "systems/abc/release-gate");
  });

  it("re-encodes characters that are legal inside one segment", () => {
    assert.equal(proxyUpstreamPath(["systems", "a b"]), "systems/a%20b");
  });

  it("refuses dot segments", () => {
    assert.equal(proxyUpstreamPath(["..", "actuator", "prometheus"]), null);
    assert.equal(proxyUpstreamPath(["systems", "."]), null);
  });

  it("refuses decoded slashes and backslashes that would add segments", () => {
    // `..%2f..%2factuator%2fprometheus` arrives as one decoded segment.
    assert.equal(proxyUpstreamPath(["../../actuator/prometheus"]), null);
    assert.equal(proxyUpstreamPath(["..\\..\\actuator"]), null);
  });

  it("refuses segments that would inject a query or fragment", () => {
    assert.equal(proxyUpstreamPath(["systems?tenant=x"]), null);
    assert.equal(proxyUpstreamPath(["systems#x"]), null);
  });

  it("refuses control characters and empty input", () => {
    assert.equal(proxyUpstreamPath(["sys\ntems"]), null);
    assert.equal(proxyUpstreamPath([""]), null);
    assert.equal(proxyUpstreamPath([]), null);
  });
});

describe("proxyTimeoutMs", () => {
  it("gives uploads and exports two minutes", () => {
    assert.equal(proxyTimeoutMs("evidence/documents/upload"), 120_000);
    assert.equal(proxyTimeoutMs("systems/abc/evidence-pack"), 120_000);
    assert.equal(proxyTimeoutMs("systems/abc/evidence-pack.pdf"), 120_000);
    assert.equal(proxyTimeoutMs("systems/abc/grc-export"), 120_000);
    assert.equal(proxyTimeoutMs("account/export"), 120_000);
    assert.equal(proxyTimeoutMs("packs/au-adm"), 120_000);
  });

  it("gives everything else thirty seconds", () => {
    assert.equal(proxyTimeoutMs("systems"), 30_000);
    assert.equal(proxyTimeoutMs("evidence/query"), 30_000);
  });
});
