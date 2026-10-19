import { NextRequest, NextResponse } from "next/server";
import { clientIpHeaders } from "@/lib/client-ip";
import { readLimitedJson } from "@/lib/read-limited-body";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";
import { verifyTurnstile } from "@/lib/turnstile";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const body = (await readLimitedJson(request)) as Record<string, unknown> | null;
  if (!body || typeof body !== "object") {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }
  const headers = clientIpHeaders(request);
  if (!(await verifyTurnstile(typeof body.turnstileToken === "string" ? body.turnstileToken : null, headers["X-Client-IP"] ?? null))) {
    return NextResponse.json({ error: "Please complete the verification challenge." }, { status: 400 });
  }
  const upstream = await fetchUpstream(`${API_BASE}/auth/password/forgot`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify({ email: body.email }),
  });
  if (!upstream || upstream.status >= 500) return serviceUnavailable();
  // Same answer whatever the API says about the address, so this endpoint cannot be used to probe accounts.
  return NextResponse.json({ status: "accepted" }, { status: 202 });
}
