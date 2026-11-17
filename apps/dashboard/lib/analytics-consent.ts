/**
 * Opt-in consent for Google Analytics. Nothing from Google loads until the visitor accepts;
 * Cloudflare Web Analytics is cookieless and runs regardless.
 */
export type AnalyticsConsent = "granted" | "denied";

export const CONSENT_STORAGE_KEY = "eu-ai-analytics-consent";

/** Fired on window to reopen the banner, e.g. from the privacy page. */
export const OPEN_CONSENT_EVENT = "eu-ai:open-cookie-settings";

/**
 * Routes Google Analytics never sees: sign-in and token links (reset, verify, invite carry secrets in the
 * query string) and every signed-in workspace route under app/(dashboard).
 * lib/analytics-consent.test.ts fails when a new (dashboard) route is missing here.
 */
export const ANALYTICS_EXCLUDED_PREFIXES = [
  "/login",
  "/signup",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
  "/invite",
  "/approvals",
  "/audit",
  "/command",
  "/contracts",
  "/corpus",
  "/evals",
  "/evidence",
  "/governance",
  "/onboarding",
  "/proposals",
  "/public-claims",
  "/readiness",
  "/reg-monitor",
  "/settings",
  "/systems",
] as const;

export function isAnalyticsExcluded(pathname: string): boolean {
  return ANALYTICS_EXCLUDED_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/** The URL GA receives: origin and path only, never the query string or fragment. */
export function analyticsLocation(origin: string, pathname: string): string {
  return `${origin}${pathname}`;
}

export function parseConsent(value: string | null | undefined): AnalyticsConsent | null {
  return value === "granted" || value === "denied" ? value : null;
}

export function readConsent(): AnalyticsConsent | null {
  try {
    return parseConsent(window.localStorage.getItem(CONSENT_STORAGE_KEY));
  } catch {
    return null;
  }
}

export function writeConsent(consent: AnalyticsConsent) {
  try {
    window.localStorage.setItem(CONSENT_STORAGE_KEY, consent);
  } catch {
    // Storage blocked: the choice holds for this page view only.
  }
}

/** GA4 sets `_ga` and `_ga_<container>` on the registrable domain; clear them on every parent domain. */
export function gaCookieNames(cookieHeader: string): string[] {
  return cookieHeader
    .split(";")
    .map((c) => c.split("=")[0].trim())
    .filter((name) => name === "_ga" || name.startsWith("_ga_") || name === "_gid");
}

export function clearGaCookies() {
  const parts = window.location.hostname.split(".");
  const domains = parts.map((_, i) => parts.slice(i).join(".")).filter((d) => d.includes("."));
  for (const name of gaCookieNames(document.cookie)) {
    document.cookie = `${name}=; Max-Age=0; path=/`;
    for (const domain of domains) document.cookie = `${name}=; Max-Age=0; path=/; domain=.${domain}`;
  }
}
