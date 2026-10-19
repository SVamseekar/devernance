import assert from "node:assert/strict";
import { test } from "node:test";

import { readLimitedJson, readLimitedText } from "./read-limited-body";

function streamed(parts: string[]): Request {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const part of parts) controller.enqueue(encoder.encode(part));
      controller.close();
    },
  });
  return new Request("http://x/", { method: "POST", body, duplex: "half" } as RequestInit);
}

test("returns the body when it fits", async () => {
  assert.equal(await readLimitedText(new Request("http://x/", { method: "POST", body: '{"a":1}' }), 16), '{"a":1}');
});

test("refuses a declared Content-Length over the limit without reading", async () => {
  const req = new Request("http://x/", { method: "POST", body: "x", headers: { "content-length": "999999" } });
  assert.equal(await readLimitedText(req, 16), null);
});

test("refuses a streamed body that grows past the limit", async () => {
  assert.equal(await readLimitedText(streamed(["a".repeat(10), "b".repeat(10)]), 16), null);
});

test("counts bytes, not characters", async () => {
  // Six two-byte characters are 12 bytes.
  assert.equal(await readLimitedText(streamed(["éééééé"]), 11), null);
  assert.equal(await readLimitedText(streamed(["éééééé"]), 12), "éééééé");
});

test("readLimitedJson parses small JSON and refuses large or malformed bodies", async () => {
  const post = (body: string) => new Request("http://x/", { method: "POST", body });
  assert.deepEqual(await readLimitedJson(post('{"email":"a@b.c"}')), { email: "a@b.c" });
  assert.equal(await readLimitedJson(post("not json")), null);
  assert.equal(await readLimitedJson(post(JSON.stringify({ x: "y".repeat(100) })), 32), null);
});
