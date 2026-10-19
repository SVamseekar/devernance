import { NextRequest, NextResponse } from "next/server";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function GET(
  _request: NextRequest,
  context: { params: Promise<{ token: string }> },
) {
  const { token } = await context.params;
  const upstream = await fetchUpstream(`${API_BASE}/auth/invites/${encodeURIComponent(token)}`, { method: "GET" });
  if (!upstream) return serviceUnavailable();
  const text = await upstream.text();
  return new NextResponse(text, {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
  });
}
