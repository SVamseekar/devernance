import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, it } from "node:test";

import { SUBPROCESSORS } from "./subprocessors";

const repo = join(__dirname, "..", "..", "..");

/** Configuration the running services read: every third-party provider shows up in one of these. */
const configText = [
  ".env.example",
  "services/api/src/main/resources/application.properties",
  "apps/dashboard/app/layout.tsx",
  "apps/dashboard/lib/turnstile.ts",
]
  .map((f) => readFileSync(join(repo, f), "utf8"))
  .join("\n");

/** Providers the app can be configured to send personal data to, and how each is recognised. */
const configuredProviders: [string, RegExp][] = [
  ["Brevo", /smtp-relay\.brevo\.com/],
  ["Oracle Cloud Infrastructure", /oci\.oraclecloud\.com/],
  ["Dodo Payments", /dodopayments\.com/],
  ["Cloudflare", /TURNSTILE|CF_BEACON/],
  ["Google Analytics", /GA_MEASUREMENT_ID/],
  ["Discord", /DISCORD_(DEMO_)?WEBHOOK_URL/],
  ["Google / Microsoft", /OAUTH_(GOOGLE|MICROSOFT)_CLIENT_ID/],
];

describe("subprocessors", () => {
  it("names every provider the app is configured to use", () => {
    const names = SUBPROCESSORS.map((s) => s.name);
    for (const [name, marker] of configuredProviders) {
      assert.match(configText, marker, `${name} is no longer configured; update this test`);
      assert.ok(names.includes(name), `${name} is configured but missing from SUBPROCESSORS`);
    }
  });

  it("gives every entry a purpose, location, data categories and an https link", () => {
    for (const s of SUBPROCESSORS) {
      assert.ok(s.purpose && s.location && s.dataCategories, s.name);
      assert.match(s.link, /^https:\/\//, s.name);
    }
  });
});
