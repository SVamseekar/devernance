import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { gateSnippets } from "./gate-snippets";

const tab = (label: string) => {
  const found = gateSnippets.find((t) => t.label === label);
  assert.ok(found, `missing tab ${label}`);
  return found.code;
};

describe("gateSnippets", () => {
  it("uses the published GitHub Action on its major tag", () => {
    const code = tab("GitHub Actions");
    assert.match(code, /uses: SVamseekar\/devernance-release-gate@v1\n/);
    for (const input of ["api-key", "system-id", "api-url"]) {
      assert.match(code, new RegExp(`${input}: \\$\\{\\{`), input);
    }
    assert.match(code, /secrets\.ASSURANCE_API_KEY/);
  });

  it("keeps a plain curl sample for other CI systems", () => {
    assert.match(tab("cURL"), /X-Api-Key: \$ASSURANCE_API_KEY/);
  });
});
