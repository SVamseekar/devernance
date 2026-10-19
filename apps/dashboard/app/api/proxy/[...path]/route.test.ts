import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { NextRequest } from "next/server";
import { GET, POST } from "./route";

const realFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = realFetch;
});

describe("API proxy", () => {
  it("forwards the visitor's IP so per-client limits apply to the visitor, not to this server", async () => {
    let sent: Record<string, string> = {};
    globalThis.fetch = async (_url, init) => {
      sent = init?.headers as Record<string, string>;
      return new Response("{}", { status: 200 });
    };
    const request = new NextRequest("http://dashboard.test/api/proxy/evidence/query", {
      method: "POST",
      headers: { cookie: "session_access=tok", "cf-connecting-ip": "198.51.100.23", "Content-Type": "application/json" },
      body: "{}",
    });
    const res = await POST(request, { params: Promise.resolve({ path: ["evidence", "query"] }) });
    assert.equal(res.status, 200);
    assert.equal(sent["X-Client-IP"], "198.51.100.23");
    assert.equal(sent["Authorization"], "Bearer tok");
  });

  it("refuses paths that would leave /api/v1 without calling the API", async () => {
    let called = false;
    globalThis.fetch = async () => {
      called = true;
      return new Response("{}", { status: 200 });
    };
    const request = new NextRequest("http://dashboard.test/api/proxy/..%2f..%2factuator%2fprometheus", {
      headers: { cookie: "session_access=tok" },
    });
    const res = await GET(request, { params: Promise.resolve({ path: ["../../actuator/prometheus"] }) });
    assert.equal(res.status, 404);
    assert.equal(called, false);
  });

  it("refuses bodies over the upload limit before calling the API", async () => {
    let called = false;
    globalThis.fetch = async () => {
      called = true;
      return new Response("{}", { status: 200 });
    };
    const request = new NextRequest("http://dashboard.test/api/proxy/evidence/documents/upload", {
      method: "POST",
      headers: { cookie: "session_access=tok", "content-length": String(27 * 1024 * 1024) },
      body: "x",
    });
    const res = await POST(request, { params: Promise.resolve({ path: ["evidence", "documents", "upload"] }) });
    assert.equal(res.status, 413);
    assert.equal(called, false);
  });

  it("replays the body after refreshing an expired access token", async () => {
    const bodies: string[] = [];
    globalThis.fetch = async (url, init) => {
      if (String(url).endsWith("/auth/refresh")) {
        return new Response(JSON.stringify({ accessToken: "new", refreshToken: "r2" }), { status: 200 });
      }
      bodies.push(new TextDecoder().decode(init?.body as Uint8Array));
      const auth = (init?.headers as Record<string, string>)["Authorization"];
      return new Response("{}", { status: auth === "Bearer new" ? 201 : 401 });
    };
    const request = new NextRequest("http://dashboard.test/api/proxy/systems", {
      method: "POST",
      headers: { cookie: "session_access=old; session_refresh=r1", "Content-Type": "application/json" },
      body: '{"name":"x"}',
    });
    const res = await POST(request, { params: Promise.resolve({ path: ["systems"] }) });
    assert.equal(res.status, 201);
    assert.deepEqual(bodies, ['{"name":"x"}', '{"name":"x"}']);
    assert.equal(res.cookies.get("session_access")?.value, "new");
  });

  it("streams the API response through with its download headers", async () => {
    globalThis.fetch = async () =>
      new Response("zip-bytes", {
        status: 200,
        headers: { "Content-Type": "application/zip", "Content-Disposition": "attachment; filename=x.zip" },
      });
    const request = new NextRequest("http://dashboard.test/api/proxy/account/export", {
      headers: { cookie: "session_access=tok" },
    });
    const res = await GET(request, { params: Promise.resolve({ path: ["account", "export"] }) });
    assert.equal(res.status, 200);
    assert.equal(res.headers.get("Content-Disposition"), "attachment; filename=x.zip");
    assert.equal(await res.text(), "zip-bytes");
  });
});
