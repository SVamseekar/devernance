import { NextRequest, NextResponse } from "next/server";
import { clientIpHeaders } from "@/lib/client-ip";
import { safeNextPath } from "@/lib/auth-redirect";
import { setSessionCookies } from "@/lib/session";
import { fetchUpstream } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";
const SUPPORTED = new Set(["google", "microsoft"]);
const OAUTH_NEXT_COOKIE = "oauth_next";
const OAUTH_NONCE_COOKIE = "oauth_nonce";

function loginRedirect(request: NextRequest, authError: string): NextResponse {
  const url = new URL("/login", request.url);
  url.searchParams.set("auth_error", authError);
  return NextResponse.redirect(url);
}

/**
 * OIDC redirect_uri target. Exchanges code+state with the Spring API, then sets
 * the same httpOnly session cookies as password login.
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> },
) {
  const { provider } = await params;
  const normalized = provider?.toLowerCase();
  if (!SUPPORTED.has(normalized)) {
    return loginRedirect(request, "unsupported_provider");
  }

  const oauthError = request.nextUrl.searchParams.get("error");
  if (oauthError) {
    const code = oauthError === "access_denied" ? "denied" : "sign_in_failed";
    return loginRedirect(request, code);
  }

  const code = request.nextUrl.searchParams.get("code");
  const state = request.nextUrl.searchParams.get("state");
  if (!code || !state) {
    return loginRedirect(request, "state");
  }

  const nonce = request.cookies.get(OAUTH_NONCE_COOKIE)?.value;
  if (!nonce) {
    return loginRedirect(request, "state");
  }

  const upstream = await fetchUpstream(`${API_BASE}/auth/oauth/${normalized}/callback`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...clientIpHeaders(request) },
    body: JSON.stringify({ code, state, nonce }),
  });
  if (!upstream) {
    return loginRedirect(request, "sign_in_unavailable");
  }

  if (!upstream.ok) {
    if (upstream.status === 429) {
      return loginRedirect(request, "sign_in_failed");
    }
    if (upstream.status === 409) {
      return loginRedirect(request, "email_unverified");
    }
    if (upstream.status === 403) {
      return loginRedirect(request, "not_provisioned");
    }
    if (upstream.status === 400) {
      return loginRedirect(request, "state");
    }
    return loginRedirect(request, "sign_in_failed");
  }

  const tokens = (await upstream.json()) as {
    accessToken?: string;
    refreshToken?: string;
  };
  if (!tokens.accessToken || !tokens.refreshToken) {
    return loginRedirect(request, "sign_in_failed");
  }

  const destination = safeNextPath(request.cookies.get(OAUTH_NEXT_COOKIE)?.value);
  const response = NextResponse.redirect(new URL(destination, request.url));
  setSessionCookies(response, tokens.accessToken, tokens.refreshToken);
  response.cookies.set(OAUTH_NEXT_COOKIE, "", {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 0,
  });
  response.cookies.set(OAUTH_NONCE_COOKIE, "", {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 0,
  });
  return response;
}
