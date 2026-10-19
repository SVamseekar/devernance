import { NextRequest, NextResponse } from "next/server";

const ACCESS_COOKIE = "session_access";
const REFRESH_COOKIE = "session_refresh";

/** Dashboard app shell routes that require a session cookie (defense in depth). */
const PROTECTED_PREFIXES = [
  "/command",
  "/systems",
  "/approvals",
  "/evidence",
  "/evals",
  "/contracts",
  "/audit",
  "/readiness",
  "/reg-monitor",
  "/corpus",
  "/proposals",
  "/settings",
  "/public-claims",
  "/onboarding",
];

function isProtectedPath(pathname: string): boolean {
  return PROTECTED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}

function hasSession(request: NextRequest): boolean {
  return (
    Boolean(request.cookies.get(ACCESS_COOKIE)?.value) ||
    Boolean(request.cookies.get(REFRESH_COOKIE)?.value)
  );
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Never bounce /login based on cookie presence — stale cookies plus a 401
  // redirect created a /login ↔ /command reload loop in production.
  if (!isProtectedPath(pathname)) {
    return NextResponse.next();
  }

  if (hasSession(request)) {
    return NextResponse.next();
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = "/login";
  loginUrl.searchParams.set("next", pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: [
    "/command",
    "/command/:path*",
    "/systems",
    "/systems/:path*",
    "/approvals",
    "/approvals/:path*",
    "/evidence",
    "/evidence/:path*",
    "/evals",
    "/evals/:path*",
    "/contracts",
    "/contracts/:path*",
    "/audit",
    "/audit/:path*",
    "/readiness",
    "/readiness/:path*",
    "/reg-monitor",
    "/reg-monitor/:path*",
    "/corpus",
    "/corpus/:path*",
    "/proposals",
    "/proposals/:path*",
    "/settings",
    "/settings/:path*",
    "/public-claims",
    "/public-claims/:path*",
    "/onboarding",
    "/onboarding/:path*",
  ],
};
