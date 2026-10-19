import { NextRequest, NextResponse } from "next/server";
import { setSessionCookies } from "@/lib/session";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";
import { clientIpHeaders } from "@/lib/client-ip";
import { readLimitedJson } from "@/lib/read-limited-body";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const body = (await readLimitedJson(request)) as { email?: unknown; password?: unknown } | null;
  if (!body || typeof body !== "object") {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }
  const email = typeof body.email === "string" ? body.email : "";
  const password = typeof body.password === "string" ? body.password : "";
  if (!email || !password) {
    return NextResponse.json({ error: "Email and password are required" }, { status: 400 });
  }

  const upstream = await fetchUpstream(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...clientIpHeaders(request) },
    body: JSON.stringify({ email, password }),
  });
  if (!upstream) return serviceUnavailable();
  if (upstream.status === 429) {
    return NextResponse.json({ error: "Too many sign-in attempts. Try again in 15 minutes." }, { status: 429 });
  }
  if (upstream.status >= 500) return serviceUnavailable();
  if (upstream.status === 403) {
    const body = await upstream.json().catch(() => null);
    const error = body?.error === "workspace_deleted" ? "workspace_deleted" : "email_not_verified";
    return NextResponse.json({ error }, { status: 403 });
  }
  if (!upstream.ok) {
    return NextResponse.json({ error: "Invalid email or password" }, { status: 401 });
  }

  const tokens = await upstream.json();
  const response = NextResponse.json({ ok: true });
  setSessionCookies(response, tokens.accessToken, tokens.refreshToken);
  return response;
}
