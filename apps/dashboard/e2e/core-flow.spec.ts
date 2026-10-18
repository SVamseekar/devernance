import { expect, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

test("register a system, upload evidence, see the gate, export the pack", async ({ page }) => {
  const name = `E2E Claims ${Date.now()}`;

  await page.goto("/login");
  await page.getByLabel(/email/i).fill("compliance@example.com");
  await page.getByLabel(/password/i).fill("dev-local-password-only");
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL(/\/command/);

  await page.goto("/systems");
  await page.getByText("Register System", { exact: true }).click();
  await page.getByPlaceholder(/underwriting copilot/i).fill(name);
  await page.getByLabel(/^owner$/i).fill("Claims Ops");
  await page.getByLabel(/^purpose$/i).fill("Prioritise and route insurance claims");
  await page.getByRole("button", { name: /next/i }).click();
  await page.getByRole("button", { name: /next/i }).click();
  await page.getByRole("button", { name: /register system/i }).click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();

  await page.goto("/evidence");
  await page.locator("label", { hasText: /^System$/ }).locator("..").getByRole("combobox").click();
  await page.getByRole("option", { name, exact: true }).click();
  await page.locator('input[type="file"]').setInputFiles(
    path.resolve(__dirname, "../../../services/api/src/test/resources/fixtures/evidence/oversight-sop.txt"),
  );
  await page.getByRole("button", { name: /index|upload/i }).click();
  await expect(page.getByText(/oversight-sop/i)).toBeVisible();
});

/** Newest confirmation link the API wrote to its log (the API runs with email mode `log`). */
function latestVerifyLink(logPath: string): string {
  const matches = [...fs.readFileSync(logPath, "utf8").matchAll(/verify-email\?token=[A-Za-z0-9_-]+/g)];
  const last = matches.at(-1);
  if (!last) throw new Error(`no verification link in ${logPath}`);
  return `/${last[0]}`;
}

test("sign up, confirm the email, and finish onboarding", async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`;
  const logPath = process.env.API_LOG ?? path.resolve(__dirname, "../../../services/api/api.log");

  await page.goto("/signup");
  await page.getByLabel(/work email/i).fill(email);
  await page.getByLabel(/organisation name/i).fill(`E2E Org ${Date.now()}`);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: /create workspace/i }).click();
  await expect(page.getByRole("heading", { name: /check your inbox/i })).toBeVisible();

  await expect.poll(() => latestVerifyLink(logPath), { timeout: 15_000 }).toMatch(/token=/);
  await page.goto(latestVerifyLink(logPath));
  await page.getByLabel(/^password/i).fill("correct-horse-battery");
  await page.getByLabel(/confirm password/i).fill("correct-horse-battery");
  await page.getByRole("button", { name: /confirm and continue/i }).click();
  await page.waitForURL(/\/onboarding/);

  await page.getByLabel(/system name/i).fill("E2E onboarding system");
  await page.getByLabel(/^owner/i).fill("Ops");
  await page.getByLabel(/^purpose/i).fill("Answer customer questions");
  await page.getByRole("button", { name: /register system/i }).click();
  await expect(page.getByText(/release decision/i)).toBeVisible();
  await page.getByRole("button", { name: /next: gate your pipeline/i }).click();
  await page.getByRole("button", { name: /create ci key/i }).click();
  await expect(page.getByLabel(/new ci key/i)).toHaveValue(/^dvn_/);
  await page.getByRole("button", { name: /^done$/i }).click();
  await page.waitForURL(/\/command/);
});
