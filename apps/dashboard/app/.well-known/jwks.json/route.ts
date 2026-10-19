import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

/**
 * Public keys that verify signed evidence packs (docs/VERIFY_EVIDENCE_PACK.md). The API is private in
 * production, so its JWKS is served here on the workspace host. Keys change rarely; caches may hold them briefly.
 */
export async function GET() {
  const upstream = await fetchUpstream(`${API_BASE}/.well-known/jwks.json`, { method: "GET" }, 5_000);
  if (!upstream || !upstream.ok) return serviceUnavailable();
  return new Response(await upstream.text(), {
    headers: {
      "Content-Type": "application/jwk-set+json",
      "Cache-Control": "public, max-age=300",
    },
  });
}
