import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { NextRequest } from "next/server";
import { GET } from "./route";

const realFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = realFetch;
});

const start = (provider: string, query = "") =>
  GET(new NextRequest(`http://dashboard.test/api/auth/oauth/${provider}/start${query}`), {
    params: Promise.resolve({ provider }),
  });

describe("GET /api/auth/oauth/[provider]/start", () => {
  it("asks the API for the authorize URL server-side and sends the browser to the provider", async () => {
    let asked = "";
    let redirectMode: RequestRedirect | undefined;
    globalThis.fetch = async (url, init) => {
      asked = String(url);
      redirectMode = init?.redirect;
      return new Response(null, {
        status: 302,
        headers: { location: "https://accounts.google.com/o/oauth2/v2/auth?state=s" },
      });
    };
    const res = await start("google", "?next=%2Fsystems");
    assert.equal(res.status, 302);
    assert.equal(res.headers.get("location"), "https://accounts.google.com/o/oauth2/v2/auth?state=s");
    assert.match(asked, /\/auth\/oauth\/google\/start\?nonce=/);
    assert.equal(redirectMode, "manual");
    assert.equal(res.cookies.get("oauth_next")?.value, "/systems");
    assert.ok(res.cookies.get("oauth_nonce")?.value);
  });

  it("drops an unsafe return path", async () => {
    globalThis.fetch = async () =>
      new Response(null, { status: 302, headers: { location: "https://login.microsoftonline.com/x" } });
    const res = await start("microsoft", "?next=%2F%5Cevil.example");
    assert.equal(res.cookies.get("oauth_next")?.value, "/command");
  });

  it("sends the visitor back to login when the API is unreachable", async () => {
    globalThis.fetch = async () => {
      throw new TypeError("fetch failed");
    };
    const res = await start("google");
    assert.equal(res.headers.get("location"), "http://dashboard.test/login?auth_error=sign_in_unavailable");
  });

  it("refuses unknown providers without calling the API", async () => {
    let called = false;
    globalThis.fetch = async () => {
      called = true;
      return new Response(null, { status: 302 });
    };
    const res = await start("github");
    assert.equal(res.headers.get("location"), "http://dashboard.test/login?auth_error=unsupported_provider");
    assert.equal(called, false);
  });
});
