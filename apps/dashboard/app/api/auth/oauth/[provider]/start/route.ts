import { randomBytes } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { safeNextPath } from "@/lib/auth-redirect";
import { clientIpHeaders } from "@/lib/client-ip";
import { fetchUpstream } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";
const SUPPORTED = new Set(["google", "microsoft"]);
const OAUTH_NEXT_COOKIE = "oauth_next";
const OAUTH_NONCE_COOKIE = "oauth_nonce";

/**
 * Browser entry for OAuth: stores safe return path, asks the Spring start endpoint for the
 * Google/Microsoft authorize URL (with a signed state parameter), and redirects there. The API is
 * asked server-side because in production it sits on a private network the browser cannot reach.
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> },
) {
  const { provider } = await params;
  const normalized = provider?.toLowerCase();
  if (!SUPPORTED.has(normalized)) {
    return NextResponse.redirect(new URL("/login?auth_error=unsupported_provider", request.url));
  }

  const next = safeNextPath(request.nextUrl.searchParams.get("next"));
  const nonce = randomBytes(24).toString("base64url");
  const upstream = await fetchUpstream(
    `${API_BASE}/auth/oauth/${normalized}/start?nonce=${encodeURIComponent(nonce)}`,
    { method: "GET", headers: clientIpHeaders(request), redirect: "manual" },
  );
  const location = upstream?.headers.get("location");
  if (!upstream || upstream.status < 300 || upstream.status >= 400 || !location) {
    return NextResponse.redirect(new URL("/login?auth_error=sign_in_unavailable", request.url));
  }
  const response = NextResponse.redirect(location, 302);
  const cookieOptions = {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge: 10 * 60,
  };
  response.cookies.set(OAUTH_NEXT_COOKIE, next, cookieOptions);
  response.cookies.set(OAUTH_NONCE_COOKIE, nonce, cookieOptions);
  return response;
}
