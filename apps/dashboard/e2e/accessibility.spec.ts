import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

// US ADA website claims and EU EN 301 549 both measure against WCAG 2.1 AA; 2.2 AA is a superset.
const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

const PUBLIC_PAGES = [
  "/",
  "/product",
  "/product/release-gate",
  "/how-it-works",
  "/eu-ai-act",
  "/eu-ai-act/article-50",
  "/pricing",
  "/faq",
  "/docs",
  "/request-demo",
  "/compare/credo-ai-alternative",
  "/terms",
  "/privacy",
  "/security",
  "/subprocessors",
  "/disclaimer",
  "/login",
  "/signup",
];

const APP_PAGES = ["/command", "/systems", "/approvals", "/evals", "/evidence", "/public-claims", "/readiness", "/reg-monitor", "/settings"];

async function expectNoViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  const summary = violations.flatMap((v) => v.nodes.map((n) => `${v.id}: ${n.target.join(" ")}`));
  expect(summary).toEqual([]);
}

test.describe("accessibility", () => {
  for (const path of PUBLIC_PAGES) {
    test(`${path} has no WCAG 2.2 AA violations`, async ({ page }) => {
      await page.goto(path, { waitUntil: "networkidle" });
      await expectNoViolations(page);
    });
  }

  test("signed-in pages have no WCAG 2.2 AA violations", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel(/email/i).fill("compliance@example.com");
    await page.getByLabel(/password/i).fill("dev-local-password-only");
    await page.getByRole("button", { name: /sign in/i }).click();
    await page.waitForURL(/\/command/);
    for (const path of APP_PAGES) {
      await test.step(path, async () => {
        await page.goto(path, { waitUntil: "networkidle" });
        await expectNoViolations(page);
      });
    }
  });
});
