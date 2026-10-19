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
  // No password here: it is chosen on the emailed link.
  const upstream = await fetchUpstream(`${API_BASE}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify({ email: body.email, organisationName: body.organisationName }),
  });
  if (!upstream) return serviceUnavailable();
  if (upstream.status >= 500) return serviceUnavailable();
  if (upstream.status === 429) {
    return NextResponse.json({ error: "Too many attempts. Try again in 15 minutes." }, { status: 429 });
  }
  if (!upstream.ok) {
    return NextResponse.json({ error: "Enter a valid work email and organisation name." }, { status: 400 });
  }
  return NextResponse.json({ status: "verification_sent" }, { status: 202 });
}
