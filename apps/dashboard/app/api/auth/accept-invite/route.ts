import { NextRequest, NextResponse } from "next/server";
import { clientIpHeaders } from "@/lib/client-ip";
import { readLimitedText } from "@/lib/read-limited-body";
import { setSessionCookies } from "@/lib/session";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const body = await readLimitedText(request, 16 * 1024);
  if (body === null) {
    return NextResponse.json({ error: "Request is too large" }, { status: 413 });
  }
  const upstream = await fetchUpstream(`${API_BASE}/auth/accept-invite`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...clientIpHeaders(request) },
    body,
  });
  if (!upstream) return serviceUnavailable();
  if (!upstream.ok) {
    const text = await upstream.text();
    return NextResponse.json(
      { error: text || "Invite could not be accepted" },
      { status: upstream.status },
    );
  }
  const tokens = await upstream.json();
  const response = NextResponse.json({ ok: true });
  setSessionCookies(response, tokens.accessToken, tokens.refreshToken);
  return response;
}
