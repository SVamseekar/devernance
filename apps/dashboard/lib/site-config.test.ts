import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { footerColumns, mainNav } from "./marketing-nav";
import { dashboardRoutes, landingNavLinks, publicRoutes, siteConfig } from "./site-config";

describe("siteConfig", () => {
  it("uses the product brand and a security contact", () => {
    assert.equal(siteConfig.shortName, "Devernance");
    assert.match(siteConfig.supportEmail, /^[^@\s]+@[^@\s]+\.[a-z]{2,}$/);
    assert.match(siteConfig.securityEmail, /^[^@\s]+@[^@\s]+\.[a-z]{2,}$/);
  });

  it("links pricing, security and docs from the header", () => {
    const hrefs = landingNavLinks.map((l) => l.href);
    for (const href of ["/product", "/pricing", "/security", "/docs"]) assert.ok(hrefs.includes(href), href);
    const reachable = new Set<string>();
    for (const entry of mainNav) {
      if ("href" in entry) reachable.add(entry.href);
      else {
        reachable.add(entry.menu.overview.href);
        for (const item of entry.menu.items) reachable.add(item.href.split("#")[0]);
      }
    }
    for (const { href } of landingNavLinks) assert.ok(reachable.has(href), `header reaches ${href}`);
  });

  it("links security, subprocessors and the changelog from the footer", () => {
    const hrefs = footerColumns.flatMap((c) => c.links.map((l) => l.href));
    for (const href of ["/security", "/subprocessors", "/changelog", "/docs"]) assert.ok(hrefs.includes(href), href);
  });

  it("keeps auth and app pages out of the sitemap", () => {
    const paths = publicRoutes.map((r) => r.path) as string[];
    for (const p of ["/login", "/signup", "/onboarding", "/verify-email", ...dashboardRoutes]) {
      assert.ok(!paths.includes(p), p);
    }
  });
});
