/**
 * Reads a request body, refusing anything over `maxBytes` without buffering it.
 * Returns null when the declared Content-Length or the streamed size exceeds the limit.
 */
export async function readLimitedBytes(request: Request, maxBytes: number): Promise<Uint8Array | null> {
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maxBytes) return null;
  if (!request.body) return new Uint8Array(0);

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel().catch(() => {});
      return null;
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

/** Same as {@link readLimitedBytes}, decoded as UTF-8 text. */
export async function readLimitedText(request: Request, maxBytes: number): Promise<string | null> {
  const bytes = await readLimitedBytes(request, maxBytes);
  return bytes === null ? null : new TextDecoder().decode(bytes);
}

/** Small JSON bodies for auth and form routes; null when too large or not JSON. */
export async function readLimitedJson(request: Request, maxBytes: number = 16 * 1024): Promise<unknown | null> {
  const text = await readLimitedText(request, maxBytes);
  if (text === null) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
