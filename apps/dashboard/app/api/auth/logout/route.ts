import { NextRequest, NextResponse } from "next/server";
import { clearSessionCookies, readRefreshToken } from "@/lib/session";
import { fetchUpstream } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const refreshToken = readRefreshToken(request);
  if (refreshToken) {
    // Best-effort revocation — cookies are cleared regardless so the browser session ends either way.
    await fetchUpstream(`${API_BASE}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    }, 5_000);
  }
  const response = NextResponse.json({ ok: true });
  clearSessionCookies(response);
  return response;
}
