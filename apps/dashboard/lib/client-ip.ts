import type { NextRequest } from "next/server";

/**
 * The visitor's IP. Cloudflare sets cf-connecting-ip and visitors cannot override it; without Cloudflare (local,
 * Vercel) the platform-set x-forwarded-for is used. Never key a limit on a header a visitor controls end to end.
 */
export function clientIp(request: NextRequest): string {
  return (
    request.headers.get("cf-connecting-ip") ??
    request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ??
    ""
  );
}

/** Real browser IP for the API's per-IP auth rate limit (trusted only when the API sits behind this BFF). */
export function clientIpHeaders(request: NextRequest): Record<string, string> {
  const ip = clientIp(request);
  return ip ? { "X-Client-IP": ip } : {};
}
