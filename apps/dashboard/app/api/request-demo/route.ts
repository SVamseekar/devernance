import { NextRequest, NextResponse } from "next/server";

import { clientIp } from "@/lib/client-ip";
import { readLimitedText } from "@/lib/read-limited-body";
import { siteConfig } from "@/lib/site-config";

export const runtime = "nodejs";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const RATE_LIMIT_MAX = 5;
const MIN_FILL_MS = 3000;

type RateEntry = { count: number; resetAt: number };

const globalForRate = globalThis as typeof globalThis & {
  __demoRateLimit?: Map<string, RateEntry>;
};

const ipHits: Map<string, RateEntry> =
  globalForRate.__demoRateLimit ?? new Map<string, RateEntry>();
globalForRate.__demoRateLimit = ipHits;

function asString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function asBoolean(value: unknown): boolean {
  return value === true || value === "true" || value === 1 || value === "1";
}

type ValidatedDemo = {
  workEmail: string;
  companyName: string;
  jobTitle: string;
  message: string;
  marketingConsent: boolean;
};

function validateDemoRequest(
  body: unknown,
): { ok: true; data: ValidatedDemo } | { ok: false; error: string } {
  if (!body || typeof body !== "object") {
    return { ok: false, error: "Invalid request body" };
  }

  const b = body as Record<string, unknown>;

  if (asString(b.website)) {
    return { ok: false, error: "Submission rejected" };
  }

  const formStartedAt = Number(b.formStartedAt);
  if (Number.isFinite(formStartedAt) && Date.now() - formStartedAt < MIN_FILL_MS) {
    return { ok: false, error: "Please take a moment to complete the form" };
  }

  const required = ["workEmail", "companyName", "jobTitle", "message"] as const;

  for (const field of required) {
    if (!asString(b[field])) {
      return { ok: false, error: `Missing required field: ${field}` };
    }
  }

  const workEmail = asString(b.workEmail);
  if (!EMAIL_RE.test(workEmail)) {
    return { ok: false, error: "Invalid work email address" };
  }

  if (!asBoolean(b.privacyConsent)) {
    return { ok: false, error: "Privacy consent is required" };
  }

  return {
    ok: true,
    data: {
      workEmail,
      companyName: asString(b.companyName).slice(0, 200),
      jobTitle: asString(b.jobTitle).slice(0, 100),
      message: asString(b.message).slice(0, 4000),
      marketingConsent: asBoolean(b.marketingConsent),
    },
  };
}

function buildDiscordEmbed(
  payload: ValidatedDemo,
  meta: { ip?: string; userAgent?: string; submittedAt: string },
) {
  const field = (name: string, value?: string) => ({
    name,
    value: value || "—",
    inline: true,
  });

  return {
    // Visitor text must never ping the channel (@everyone, role or user mentions).
    allowed_mentions: { parse: [] },
    embeds: [
      {
        title: `New demo request — ${payload.companyName}`,
        description: `${payload.workEmail} · ${payload.jobTitle}`,
        color: 0x4f46e5,
        fields: [
          field("Work email", payload.workEmail),
          field("Company", payload.companyName),
          field("Role", payload.jobTitle),
          field("Marketing consent", payload.marketingConsent ? "Yes" : "No"),
          { name: "How can we help?", value: payload.message.slice(0, 1024) || "—", inline: false },
        ],
        footer: {
          text: `IP: ${meta.ip || "—"} · ${meta.userAgent || "—"}`.slice(0, 2048),
        },
        timestamp: meta.submittedAt,
      },
    ],
  };
}

const MAX_TRACKED_IPS = 10_000;
const MAX_BODY_BYTES = 16 * 1024;

function isRateLimited(ip: string | undefined): boolean {
  if (!ip) return false;
  const now = Date.now();
  if (ipHits.size > MAX_TRACKED_IPS) {
    for (const [key, entry] of ipHits) {
      if (now > entry.resetAt) ipHits.delete(key);
    }
  }
  const entry = ipHits.get(ip);
  if (!entry || now > entry.resetAt) {
    ipHits.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
    return false;
  }
  entry.count += 1;
  return entry.count > RATE_LIMIT_MAX;
}

async function sendDemoRequestNotification(
  payload: ValidatedDemo,
  meta: { ip?: string; userAgent?: string; submittedAt: string },
): Promise<void> {
  const webhookUrl =
    process.env.DISCORD_DEMO_WEBHOOK_URL?.trim() ||
    process.env.DISCORD_WEBHOOK_URL?.trim();

  if (!webhookUrl) {
    throw new Error("DISCORD_WEBHOOK_URL is required");
  }

  const response = await fetch(webhookUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(buildDiscordEmbed(payload, meta)),
    signal: AbortSignal.timeout(5_000),
  });

  if (!response.ok) {
    throw new Error(`Discord webhook responded with ${response.status}`);
  }
}

export async function POST(request: NextRequest) {
  const ip = clientIp(request) || undefined;
  if (isRateLimited(ip)) {
    return NextResponse.json(
      { error: "Too many requests. Please try again later." },
      { status: 429 },
    );
  }

  const text = await readLimitedText(request, MAX_BODY_BYTES);
  if (text === null) {
    return NextResponse.json({ error: "Request is too large" }, { status: 413 });
  }
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return NextResponse.json({ error: "Invalid request body" }, { status: 400 });
  }

  const parsed = validateDemoRequest(body);
  if (!parsed.ok) {
    return NextResponse.json({ error: parsed.error }, { status: 400 });
  }

  try {
    await sendDemoRequestNotification(parsed.data, {
      submittedAt: new Date().toISOString(),
      userAgent: request.headers.get("user-agent") ?? undefined,
      ip,
    });
    return NextResponse.json({ ok: true });
  } catch (error) {
    console.error("Demo request notification failed:", error);
    return NextResponse.json(
      {
        error: `Unable to send your request right now. Please email ${siteConfig.supportEmail} directly.`,
      },
      { status: 503 },
    );
  }
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: {
      Allow: "POST, OPTIONS",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    },
  });
}
