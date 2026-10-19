/** Marketing and auth routes — a 401 here must never hard-redirect to /login. */
const PUBLIC_PATH_PREFIXES = [
  "/",
  "/login",
  "/product",
  "/how-it-works",
  "/who-its-for",
  "/pricing",
  "/method",
  "/faq",
  "/request-demo",
  "/privacy",
  "/terms",
  "/refunds",
  "/disclaimer",
  "/dpa",
  "/msa",
  "/order-form",
  "/invite",
  "/signup",
  "/verify-email",
  "/forgot-password",
  "/reset-password",
] as const;

/** Safe same-origin path for post-login redirects (`?next=` / OAuth return). */
export function safeNextPath(value: string | null | undefined, fallback = "/command"): string {
  if (!value) return fallback;
  if (!value.startsWith("/") || value.startsWith("//")) return fallback;
  // Browsers read `\` as `/` and drop tabs and newlines, so `/\evil.example` and `/\t/evil.example` leave the site.
  if (/[\\\u0000-\u001f\u007f]/.test(value)) return fallback;
  if (value.startsWith("/api/") || value.startsWith("/login")) return fallback;
  const origin = "https://same-origin.invalid";
  if (new URL(value, origin).origin !== origin) return fallback;
  return value;
}

export function shouldHardRedirectToLogin(pathname: string): boolean {
  if (!pathname || pathname.startsWith("/api/")) return false;
  return !PUBLIC_PATH_PREFIXES.some(
    (prefix) => pathname === prefix || (prefix !== "/" && pathname.startsWith(`${prefix}/`)),
  );
}

/** Destination for a 401, or null when a redirect would reload a public page (flicker loop). */
export function loginRedirectHref(pathname: string, search = ""): string | null {
  if (!shouldHardRedirectToLogin(pathname)) return null;
  const next = `${pathname}${search}`;
  const params = new URLSearchParams();
  if (next.startsWith("/") && !next.startsWith("//")) {
    params.set("next", next);
  }
  const qs = params.toString();
  return qs ? `/login?${qs}` : "/login";
}
