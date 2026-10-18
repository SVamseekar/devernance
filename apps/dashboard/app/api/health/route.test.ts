import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { GET } from "./route";

const realFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = realFetch;
});

function upstream(status: number, body: unknown) {
  return async () =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("GET /api/health", () => {
  it("is 200 when the API reports UP, and asks the API's actuator health endpoint", async () => {
    let url = "";
    globalThis.fetch = async (u) => {
      url = String(u);
      return upstream(200, { status: "UP" })();
    };
    const res = await GET();
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { status: "ok", api: "up" });
    assert.match(url, /\/actuator\/health$/);
    assert.equal(res.headers.get("Cache-Control"), "no-store");
  });

  it("is 503 when the API reports DOWN", async () => {
    globalThis.fetch = upstream(503, { status: "DOWN", components: { db: { status: "DOWN" } } });
    const res = await GET();
    assert.equal(res.status, 503);
    assert.deepEqual(await res.json(), { status: "degraded", api: "down" });
  });

  it("is 503 when the API is unreachable", async () => {
    globalThis.fetch = async () => {
      throw new TypeError("fetch failed");
    };
    const res = await GET();
    assert.equal(res.status, 503);
    assert.deepEqual(await res.json(), { status: "degraded", api: "unreachable" });
  });

  it("does not pass API health details through", async () => {
    globalThis.fetch = upstream(200, { status: "UP", components: { db: { details: { database: "PostgreSQL" } } } });
    const body = await (await GET()).text();
    assert.doesNotMatch(body, /PostgreSQL|components/);
  });
});
