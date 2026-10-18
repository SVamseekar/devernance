import { NextResponse } from "next/server";
import { fetchUpstream } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export const dynamic = "force-dynamic";

/**
 * Uptime probe for the site and the API behind it (UptimeRobot). Answers only up/down,
 * never the API's health details.
 */
export async function GET() {
  const upstream = await fetchUpstream(`${API_BASE}/actuator/health`, { method: "GET" }, 5_000);
  let api: "up" | "down" | "unreachable" = "unreachable";
  if (upstream) {
    const body = (await upstream.json().catch(() => null)) as { status?: string } | null;
    api = upstream.ok && body?.status === "UP" ? "up" : "down";
  }
  return NextResponse.json(
    { status: api === "up" ? "ok" : "degraded", api },
    { status: api === "up" ? 200 : 503, headers: { "Cache-Control": "no-store" } },
  );
}
