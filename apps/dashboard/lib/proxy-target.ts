/** Largest request body the BFF forwards: the API's 26 MB multipart limit (25 MB file plus form fields). */
export const PROXY_MAX_BODY_BYTES = 26 * 1024 * 1024;

const DEFAULT_TIMEOUT_MS = 30_000;
/** Uploads extract text and embed every chunk; exports run the evgraph scan and render PDFs or zips. */
const LONG_TIMEOUT_MS = 120_000;
const LONG_RUNNING = [/^evidence\/documents\/upload$/, /export/, /^systems\/[^/]+\/evidence-pack(\.pdf)?$/, /^packs\//];

/**
 * Builds the API path under `/api/v1/` from the catch-all segments, which Next.js has already percent-decoded.
 * Returns null for anything that could step outside `/api/v1/` or change the query string: empty, `.` or `..`
 * segments, and segments holding a slash, backslash, `?`, `#` or control character. Each segment is
 * re-encoded so whatever reaches the API is exactly one path segment.
 */
export function proxyUpstreamPath(segments: readonly string[]): string | null {
  if (segments.length === 0) return null;
  for (const segment of segments) {
    if (segment === "" || segment === "." || segment === "..") return null;
    if (/[/\\?#\u0000-\u001f\u007f]/.test(segment)) return null;
  }
  return segments.map(encodeURIComponent).join("/");
}

export function proxyTimeoutMs(path: string): number {
  return LONG_RUNNING.some((pattern) => pattern.test(path)) ? LONG_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
}
