import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { API_KEY_SCOPES, DEFAULT_API_KEY_SCOPE, apiKeyScopeLabel } from "./api-key-scope";

describe("API key scopes", () => {
  it("defaults new keys to the release gate only", () => {
    assert.equal(DEFAULT_API_KEY_SCOPE, "RELEASE_GATE");
    assert.equal(API_KEY_SCOPES[0].value, "RELEASE_GATE");
  });
  it("labels each scope with what the key can do", () => {
    assert.match(apiKeyScopeLabel("RELEASE_GATE"), /release gate only/i);
    assert.match(apiKeyScopeLabel("FULL"), /full access/i);
  });
  it("shows keys listed without a scope as full access, as the API treats them", () => {
    assert.match(apiKeyScopeLabel(undefined), /full access/i);
  });
});
