import { NextRequest, NextResponse } from "next/server";
import {
  clearSessionCookies,
  readAccessToken,
  readRefreshToken,
  refreshAccessToken,
  setSessionCookies,
} from "@/lib/session";
import { clientIpHeaders } from "@/lib/client-ip";
import { PROXY_MAX_BODY_BYTES, proxyTimeoutMs, proxyUpstreamPath } from "@/lib/proxy-target";
import { readLimitedBytes } from "@/lib/read-limited-body";
import { fetchUpstream, serviceUnavailable } from "@/lib/upstream";

const API_BASE = process.env.ASSURANCE_API_BASE_URL ?? "http://localhost:8080";

async function forward(
  request: NextRequest,
  path: string,
  body: Uint8Array | undefined,
  accessToken: string,
): Promise<Response | null> {
  const targetUrl = `${API_BASE}/api/v1/${path}${request.nextUrl.search}`;
  // The visitor's IP lets the API apply per-client limits to the visitor, not to this server.
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    ...clientIpHeaders(request),
  };
  const contentType = request.headers.get("Content-Type");
  if (contentType) {
    headers["Content-Type"] = contentType;
  } else if (body && body.byteLength > 0) {
    headers["Content-Type"] = "application/json";
  }
  return fetchUpstream(
    targetUrl,
    {
      method: request.method,
      headers,
      body: body && body.byteLength > 0 ? (body as BodyInit) : undefined,
    },
    proxyTimeoutMs(path),
  );
}

function passthroughHeaders(upstream: Response): Headers {
  const headers = new Headers();
  const contentType = upstream.headers.get("Content-Type");
  if (contentType) headers.set("Content-Type", contentType);
  const disposition = upstream.headers.get("Content-Disposition");
  if (disposition) headers.set("Content-Disposition", disposition);
  const contentSha = upstream.headers.get("X-Content-Sha256");
  if (contentSha) headers.set("X-Content-Sha256", contentSha);
  const evidenceSignature = upstream.headers.get("X-Evidence-Signature");
  if (evidenceSignature) headers.set("X-Evidence-Signature", evidenceSignature);
  const retryAfter = upstream.headers.get("Retry-After");
  if (retryAfter) headers.set("Retry-After", retryAfter);
  // Expose custom header to browser JS for PDF export seal display
  headers.set("Access-Control-Expose-Headers", "X-Content-Sha256, X-Evidence-Signature, Content-Disposition");
  return headers;
}

async function handle(request: NextRequest, segments: string[]): Promise<NextResponse> {
  const path = proxyUpstreamPath(segments);
  if (!path) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }

  let accessToken = readAccessToken(request);
  let sessionCookies: { accessToken: string; refreshToken: string } | null = null;

  // Access cookie is short-lived (15m). Refresh when missing so proxy-allowed
  // sessions with only session_refresh do not 401 every API call.
  if (!accessToken) {
    const refreshToken = readRefreshToken(request);
    const rotated = refreshToken ? await refreshAccessToken(refreshToken) : null;
    if (!rotated) {
      const failed = NextResponse.json({ error: "Not authenticated" }, { status: 401 });
      clearSessionCookies(failed);
      return failed;
    }
    accessToken = rotated.accessToken;
    sessionCookies = rotated;
  }

  // Read once, capped, so the body can be replayed after a token refresh without holding unbounded uploads.
  let body: Uint8Array | undefined;
  if (!["GET", "HEAD"].includes(request.method)) {
    const limited = await readLimitedBytes(request, PROXY_MAX_BODY_BYTES);
    if (limited === null) {
      return NextResponse.json({ error: "payload_too_large", message: "Files up to 25 MB are supported." }, { status: 413 });
    }
    body = limited;
  }

  let upstream = await forward(request, path, body, accessToken);
  if (!upstream) return serviceUnavailable();

  if (upstream.status === 401) {
    await upstream.body?.cancel().catch(() => {});
    const refreshToken = readRefreshToken(request);
    const rotated = refreshToken ? await refreshAccessToken(refreshToken) : null;
    if (!rotated) {
      const failed = NextResponse.json({ error: "Session expired" }, { status: 401 });
      clearSessionCookies(failed);
      return failed;
    }
    upstream = await forward(request, path, body, rotated.accessToken);
    if (!upstream) return serviceUnavailable();
    sessionCookies = rotated;
  }

  // Streamed, not buffered: workspace exports and evidence packs can be large.
  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: passthroughHeaders(upstream),
  });
  if (sessionCookies) {
    setSessionCookies(response, sessionCookies.accessToken, sessionCookies.refreshToken);
  }
  return response;
}

export async function GET(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handle(request, (await params).path);
}
export async function POST(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handle(request, (await params).path);
}
export async function PATCH(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handle(request, (await params).path);
}
export async function PUT(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handle(request, (await params).path);
}
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handle(request, (await params).path);
}
