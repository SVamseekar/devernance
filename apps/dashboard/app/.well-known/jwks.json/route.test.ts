import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { GET } from "./route";

const realFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = realFetch;
});

describe("GET /.well-known/jwks.json", () => {
  it("serves the API's public keys with a short public cache", async () => {
    let asked = "";
    globalThis.fetch = async (url) => {
      asked = String(url);
      return new Response('{"keys":[{"kid":"k1"}]}', { status: 200 });
    };
    const res = await GET();
    assert.equal(res.status, 200);
    assert.match(asked, /\/\.well-known\/jwks\.json$/);
    assert.equal(res.headers.get("Content-Type"), "application/jwk-set+json");
    assert.equal(res.headers.get("Cache-Control"), "public, max-age=300");
    assert.deepEqual(await res.json(), { keys: [{ kid: "k1" }] });
  });

  it("answers 503 when the API is unreachable", async () => {
    globalThis.fetch = async () => {
      throw new TypeError("fetch failed");
    };
    assert.equal((await GET()).status, 503);
  });
});
