import { expect, test, type Browser, type Page } from "@playwright/test";

// The local H2 API seeds this compliance-officer key (docs/API.md); it sets up drift without spending sign-ins.
const DEV_KEY = "00000000-0000-0000-0000-000000000a01";
const API = process.env.E2E_API_URL ?? "http://localhost:8080";

async function signIn(page: Page, who: string) {
  await page.goto("/login");
  await page.getByLabel(/email/i).fill(`${who}@example.com`);
  await page.getByLabel(/password/i).fill("dev-local-password-only");
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL(/\/command/);
}

async function newSignedInPage(browser: Browser, who: string) {
  const context = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  const page = await context.newPage();
  await signIn(page, who);
  return page;
}

test("sign-off, eval reporting and drift closure follow separation of duties", async ({ page, browser, request }) => {
  const name = `E2E Underwriting ${Date.now()}`;
  await signIn(page, "engineering");

  // Register a high-risk system with the registry facts buyers ask about.
  await page.goto("/systems");
  await page.getByText("Register System", { exact: true }).click();
  await page.getByPlaceholder(/underwriting copilot/i).fill(name);
  await page.getByLabel(/^owner$/i).fill("Underwriting Platform");
  await page.getByLabel(/^purpose$/i).fill("Proposes a risk tier for health insurance applicants");
  await page.getByLabel(/^vendor$/i).fill("Microsoft (Azure OpenAI)");
  await page.getByLabel(/^model$/i).fill("gpt-4o-mini");
  await page.getByLabel(/^model version$/i).fill("2024-07-18");
  await page.getByLabel(/data sources/i).fill("Health declaration, Medical reports");
  await page.getByLabel(/people affected/i).fill("Applicants, Underwriters");
  await page.getByRole("button", { name: /next/i }).click();
  await page.getByRole("button", { name: /next/i }).click();
  await page.getByRole("button", { name: /register system/i }).click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
  // Registration hands over to the obligation-map wizard; close it to get back to the registry.
  await page.getByRole("button", { name: "Close dialog" }).click();

  const systems = await (await request.get(`${API}/api/v1/systems`, { headers: { "X-Api-Key": DEV_KEY } })).json();
  const system = systems.find((s: { name: string }) => s.name === name);
  expect(system.riskClass).toBe("HIGH");
  expect(system.vendorName).toBe("Microsoft (Azure OpenAI)");
  expect(system.dataSources).toEqual(["Health declaration", "Medical reports"]);

  // The sign-off tab says why the gate is not PASS, and engineering cannot pass its own controls.
  await page.getByRole("heading", { name, exact: true }).click();
  await page.getByRole("button", { name: "Sign-off", exact: true }).click();
  await expect(page.getByText("High-risk system is missing required human oversight evidence")).toBeVisible();
  await expect(page.getByText(/^Approval pending: ENG_LEAD_REVIEW \(\w+ cycle\)$/)).toBeVisible();
  await expect(page.getByText(/^Annex IV sections IV\.1, .* are missing from the conformity dossier$/)).toBeVisible();
  const oversight = page.getByLabel("Status for Human oversight", { exact: false }).first();
  await expect(oversight.locator("option", { hasText: "PASS" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Save dossier" })).toHaveCount(0);

  // Queueing an eval records versions and explains the signed callback; no numbers are invented.
  await page.goto("/evals");
  await page.getByRole("combobox", { name: "System" }).click();
  await page.getByRole("option", { name, exact: true }).click();
  await page.getByLabel("Model version").fill("gpt-4o-mini-2024-07-18");
  await page.getByLabel("Prompt version").fill("underwriting-v12");
  await page.getByRole("button", { name: "Queue eval run" }).click();
  await expect(page.getByText(/queued for E2E Underwriting/)).toBeVisible();
  await expect(page.getByText(/X-Eval-Signature: v1=\$SIG/)).toBeVisible();
  await expect(page.getByText(/faithfulness:\s+\d+%/)).toHaveCount(0);

  // A data contract registered in the UI; the pipeline then reports a special-category breach.
  await page.goto("/contracts");
  await page.getByLabel("AI system").selectOption({ label: name });
  await page.getByLabel("Contract name").fill(`Applicant features ${Date.now()}`);
  await page.getByLabel("Owner").fill("Data Platform");
  await page.getByLabel("Version").fill("2026-10");
  await page.getByRole("button", { name: "Register contract" }).click();
  const listContracts = async () =>
    (await request.get(`${API}/api/v1/data-contracts?systemId=${system.id}`, { headers: { "X-Api-Key": DEV_KEY } })).json();
  await expect.poll(async () => (await listContracts()).length).toBe(1);
  const contracts = await listContracts();
  const breach = await request.post(`${API}/api/v1/data-contracts/${contracts[0].id}/drift-events`, {
    headers: { "X-Api-Key": DEV_KEY },
    data: { severity: "breach", field: "genetic_test_result", description: "Special category column appeared" },
  });
  expect(breach.status()).toBe(201);

  await page.reload();
  const card = page.locator("div", { hasText: "genetic_test_result" }).filter({ has: page.getByRole("button", { name: "Resolve" }) }).last();
  await card.getByLabel("What was done about it").fill("Dropped the column at ingestion");
  await card.getByRole("button", { name: "Resolve" }).click();
  await expect(card.getByRole("alert")).toContainText("Only compliance or an admin can resolve a data contract breach");

  // Compliance closes it with a note; the note is kept with the event.
  const officer = await newSignedInPage(browser, "compliance");
  await officer.goto("/contracts");
  const officerCard = officer.locator("div", { hasText: "genetic_test_result" }).filter({ has: officer.getByRole("button", { name: "Resolve" }) }).last();
  await officerCard.getByLabel("What was done about it").fill("DPO confirmed no genetic data reached the model");
  await officerCard.getByRole("button", { name: "Resolve" }).click();
  await expect(officer.getByText("genetic_test_result")).toHaveCount(0);
  const events = await (
    await request.get(`${API}/api/v1/data-contracts/${contracts[0].id}/drift-events`, { headers: { "X-Api-Key": DEV_KEY } })
  ).json();
  expect(events[0].status).toBe("RESOLVED");
  expect(events[0].resolutionNote).toBe("DPO confirmed no genetic data reached the model");
  await officer.context().close();
});
