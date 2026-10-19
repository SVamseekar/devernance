import { NextRequest, NextResponse } from "next/server";
import { clientIpHeaders } from "@/lib/client-ip";
import { readLimitedJson } from "@/lib/read-limited-body";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const body = (await readLimitedJson(request)) as Record<string, unknown> | null;
  if (!body || typeof body !== "object") {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }
  const upstream = await fetchUpstream(`${API_BASE}/auth/password/reset`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...clientIpHeaders(request) },
    body: JSON.stringify({ token: body.token, newPassword: body.newPassword }),
  });
  if (!upstream || upstream.status >= 500) return serviceUnavailable();
  if (upstream.status === 204) return new NextResponse(null, { status: 204 });
  if (upstream.status === 410) {
    return NextResponse.json({ error: "This link is invalid, already used, or expired." }, { status: 410 });
  }
  if (upstream.status === 429) {
    return NextResponse.json({ error: "Too many attempts. Try again in 15 minutes." }, { status: 429 });
  }
  return NextResponse.json({ error: "Use a password of 12 to 128 characters." }, { status: 400 });
}
