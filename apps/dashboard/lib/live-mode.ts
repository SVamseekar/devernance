/**
 * Mock/demo fallback is for local development only.
 * Production (NODE_ENV=production) never paints seeded Claims Triage as live data.
 * Set NEXT_PUBLIC_USE_MOCK_DATA=true to force mocks, =false to force live-only.
 */
export function allowMockFallback(): boolean {
  const flag = process.env.NEXT_PUBLIC_USE_MOCK_DATA?.trim().toLowerCase();
  if (flag === "true" || flag === "1") return true;
  if (flag === "false" || flag === "0") return false;
  return process.env.NODE_ENV !== "production";
}
