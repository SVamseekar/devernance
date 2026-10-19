import { NextRequest, NextResponse } from "next/server";
import { readLimitedBytes } from "@/lib/read-limited-body";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

/**
 * Dodo's webhook signature covers the exact request bytes, so the body is forwarded untouched
 * (never parsed and re-serialised) together with the three Standard Webhooks headers.
 */
export async function POST(request: NextRequest) {
  const raw = await readLimitedBytes(request, 1024 * 1024);
  if (raw === null) return new NextResponse(null, { status: 413 });
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  for (const name of ["webhook-id", "webhook-timestamp", "webhook-signature"]) {
    const value = request.headers.get(name);
    if (value) headers[name] = value;
  }
  const upstream = await fetchUpstream(`${API_BASE}/api/v1/billing/webhooks/dodo`, {
    method: "POST",
    headers,
    body: raw as BodyInit,
  });
  if (!upstream) return serviceUnavailable(); // Dodo retries on failure
  return new NextResponse(null, { status: upstream.status });
}
