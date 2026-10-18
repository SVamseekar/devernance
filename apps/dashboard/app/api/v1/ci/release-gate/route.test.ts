import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { NextRequest } from "next/server";
import { GET } from "./route";

const realFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = realFetch;
});

function request(headers: Record<string, string> = {}) {
  return new NextRequest("http://dashboard.test/api/v1/ci/release-gate?systemId=abc", { headers });
}

describe("GET /api/v1/ci/release-gate", () => {
  it("forwards the API key and query to the API and returns its answer unchanged", async () => {
    let url = "";
    let sent: Record<string, string> = {};
    globalThis.fetch = async (u, init) => {
      url = String(u);
      sent = init?.headers as Record<string, string>;
      return new Response(JSON.stringify({ decision: "PASS", exitCode: 0 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };
    const res = await GET(request({ "X-Api-Key": "dvn_key", Cookie: "session_access=secret" }));
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { decision: "PASS", exitCode: 0 });
    assert.match(url, /\/api\/v1\/ci\/release-gate\?systemId=abc$/);
    assert.equal(sent["X-Api-Key"], "dvn_key");
    assert.equal(sent["Cookie"], undefined);
    assert.equal(sent["Authorization"], undefined);
  });

  it("passes a rejected key through as 401", async () => {
    globalThis.fetch = async () => new Response("{}", { status: 401 });
    assert.equal((await GET(request({ "X-Api-Key": "dvn_bad" }))).status, 401);
  });

  it("answers 401 without calling the API when no key is sent", async () => {
    let called = false;
    globalThis.fetch = async () => {
      called = true;
      return new Response("{}");
    };
    assert.equal((await GET(request())).status, 401);
    assert.equal(called, false);
  });

  it("answers 503 when the API is unreachable", async () => {
    globalThis.fetch = async () => {
      throw new TypeError("fetch failed");
    };
    assert.equal((await GET(request({ "X-Api-Key": "dvn_key" }))).status, 503);
  });
});
