import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it } from "node:test";
import { NextRequest } from "next/server";
import { POST } from "./route";

const realFetch = globalThis.fetch;
const form = {
  workEmail: "buyer@example.com",
  companyName: "Example GmbH",
  jobTitle: "CISO",
  message: "@everyone we need a demo",
  privacyConsent: true,
  formStartedAt: 0,
};

beforeEach(() => {
  process.env.DISCORD_DEMO_WEBHOOK_URL = "https://discord.test/webhook";
  (globalThis as { __demoRateLimit?: Map<string, unknown> }).__demoRateLimit?.clear();
});
afterEach(() => {
  globalThis.fetch = realFetch;
  delete process.env.DISCORD_DEMO_WEBHOOK_URL;
});

const submit = (headers: Record<string, string>, body: string = JSON.stringify(form)) =>
  POST(new NextRequest("http://dashboard.test/api/request-demo", { method: "POST", headers, body }));

describe("POST /api/request-demo", () => {
  it("never lets visitor text ping the Discord channel", async () => {
    let sent: { allowed_mentions?: { parse: string[] } } = {};
    globalThis.fetch = async (_url, init) => {
      sent = JSON.parse(String(init?.body));
      return new Response(null, { status: 204 });
    };
    const res = await submit({ "cf-connecting-ip": "198.51.100.10" });
    assert.equal(res.status, 200);
    assert.deepEqual(sent.allowed_mentions, { parse: [] });
  });

  it("limits by the Cloudflare visitor IP even when X-Forwarded-For changes", async () => {
    globalThis.fetch = async () => new Response(null, { status: 204 });
    const statuses: number[] = [];
    for (let i = 0; i < 6; i++) {
      const res = await submit({ "cf-connecting-ip": "198.51.100.11", "x-forwarded-for": `10.0.0.${i}` });
      statuses.push(res.status);
    }
    assert.deepEqual(statuses, [200, 200, 200, 200, 200, 429]);
  });

  it("refuses oversized bodies without parsing them", async () => {
    globalThis.fetch = async () => new Response(null, { status: 204 });
    const res = await submit({ "cf-connecting-ip": "198.51.100.12" }, JSON.stringify({ ...form, message: "x".repeat(20_000) }));
    assert.equal(res.status, 413);
  });
});
