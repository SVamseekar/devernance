import { NextResponse } from "next/server";

const DEFAULT_TIMEOUT_MS = 10_000;

/** Fetch the Spring API; returns null instead of throwing when it is unreachable or slow. */
export async function fetchUpstream(
  url: string,
  init?: RequestInit,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<Response | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal, cache: "no-store" });
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export function serviceUnavailable(): NextResponse {
  return NextResponse.json(
    { error: "service_unavailable", message: "Devernance is temporarily unavailable. Try again shortly." },
    { status: 503 },
  );
}
