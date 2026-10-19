import { NextRequest, NextResponse } from "next/server";
import { clientIpHeaders } from "@/lib/client-ip";
import { readLimitedJson } from "@/lib/read-limited-body";
import { setSessionCookies } from "@/lib/session";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const body = (await readLimitedJson(request)) as Record<string, unknown> | null;
  if (!body || typeof body !== "object") {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }
  const headers = { "Content-Type": "application/json", ...clientIpHeaders(request) };

  if (request.nextUrl.searchParams.get("resend") === "1") {
    const resent = await fetchUpstream(`${API_BASE}/auth/verify-email/resend`, {
      method: "POST",
      headers,
      body: JSON.stringify({ email: body.email }),
    });
    if (!resent || resent.status >= 500) return serviceUnavailable();
    return NextResponse.json({ status: "accepted" }, { status: 202 });
  }

  const upstream = await fetchUpstream(`${API_BASE}/auth/verify-email`, {
    method: "POST",
    headers,
    body: JSON.stringify({ token: body.token, password: body.password }),
  });
  if (!upstream) return serviceUnavailable();
  if (upstream.status >= 500) return serviceUnavailable();
  if (upstream.status === 429) {
    return NextResponse.json({ error: "Too many attempts. Try again in 15 minutes." }, { status: 429 });
  }
  if (upstream.status === 410) {
    return NextResponse.json({ error: "This link is invalid, already used, or expired." }, { status: 410 });
  }
  if (!upstream.ok) {
    return NextResponse.json({ error: "Use a password of 12 to 128 characters." }, { status: 400 });
  }
  const tokens = await upstream.json();
  const response = NextResponse.json({ ok: true });
  setSessionCookies(response, tokens.accessToken, tokens.refreshToken);
  return response;
}
