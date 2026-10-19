const siteUrl = process.env.NEXT_PUBLIC_SITE_URL?.trim() || "https://euassuranceai.souravamseekar.com";

/** Contact mailbox on the live site. Support and security share it until devernance.com and its mailbox are live (Plan 10). */
const contactEmail = "euassuranceai@souravamseekar.com";

export const siteConfig = {
  name: "Devernance",
  shortName: "Devernance",
  /** Name before the October 2026 rename; JSON-LD lists it as alternateName so search engines link the two. */
  formerName: "EU AI Assurance OS",
  tagline: "Release gates and signed evidence packs for AI features",
  description:
    "Your pipeline already produces the evidence. Devernance checks it, gates the release in CI, and hands buyers a signed evidence pack, mapped to the EU AI Act. Evidence and readiness, not legal advice.",
  url: siteUrl,
  /** The dashboard is served by the same app as the marketing site. */
  appUrl: process.env.NEXT_PUBLIC_APP_URL?.trim() || siteUrl,
  locale: "en_GB",
  supportEmail: contactEmail,
  securityEmail: contactEmail,
  ownerName: "Marti Soura Vamseekar",
  githubUrl: "https://github.com/SVamseekar/devernance",
  evgraphUrl: "https://github.com/SVamseekar/evgraph",
  /** Public status page; the footer shows a Status link only when this is set. */
  statusUrl: process.env.NEXT_PUBLIC_STATUS_URL?.trim() || null,
  legalLastUpdated: "18 October 2026",
};

/** Authenticated dashboard routes — excluded from sitemap, disallowed in robots.txt */
export const dashboardRoutes = [
  "/command",
  "/systems",
  "/approvals",
  "/evidence",
  "/evals",
  "/contracts",
  "/audit",
  "/readiness",
  "/reg-monitor",
  "/settings",
  "/public-claims",
  "/corpus",
  "/proposals",
  "/onboarding",
  "/login",
] as const;

/** Public marketing routes included in sitemap */
export const publicRoutes = [
  { path: "/", changeFrequency: "weekly" as const, priority: 1 },
  { path: "/product", changeFrequency: "weekly" as const, priority: 0.9 },
  { path: "/product/evidence", changeFrequency: "monthly" as const, priority: 0.7 },
  { path: "/product/evaluations", changeFrequency: "monthly" as const, priority: 0.7 },
  { path: "/product/data-contracts", changeFrequency: "monthly" as const, priority: 0.7 },
  { path: "/product/release-gate", changeFrequency: "monthly" as const, priority: 0.8 },
  { path: "/eu-ai-act", changeFrequency: "weekly" as const, priority: 0.9 },
  { path: "/blog", changeFrequency: "weekly" as const, priority: 0.7 },
  { path: "/how-it-works", changeFrequency: "weekly" as const, priority: 0.8 },
  { path: "/who-its-for", changeFrequency: "monthly" as const, priority: 0.7 },
  { path: "/pricing", changeFrequency: "monthly" as const, priority: 0.6 },
  { path: "/method", changeFrequency: "weekly" as const, priority: 0.8 },
  { path: "/faq", changeFrequency: "monthly" as const, priority: 0.7 },
  { path: "/request-demo", changeFrequency: "monthly" as const, priority: 0.9 },
  { path: "/privacy", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/terms", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/refunds", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/disclaimer", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/dpa", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/msa", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/order-form", changeFrequency: "yearly" as const, priority: 0.4 },
  { path: "/security", changeFrequency: "monthly" as const, priority: 0.6 },
  { path: "/subprocessors", changeFrequency: "monthly" as const, priority: 0.4 },
  { path: "/changelog", changeFrequency: "weekly" as const, priority: 0.5 },
  { path: "/docs", changeFrequency: "monthly" as const, priority: 0.7 },
  { path: "/tools/ai-act-check", changeFrequency: "monthly" as const, priority: 0.9 },
  { path: "/tools/ai-act-deadlines", changeFrequency: "monthly" as const, priority: 0.8 },
] as const;

/** Key destinations every visitor must reach from the header (the mega menus in lib/marketing-nav.ts link each one). */
export const landingNavLinks = [
  { href: "/product", label: "Product" },
  { href: "/how-it-works", label: "How it works" },
  { href: "/pricing", label: "Pricing" },
  { href: "/security", label: "Security" },
  { href: "/docs", label: "Docs" },
] as const;

/** Primary app nav — keep in sync with sidebar + proxy.ts protected prefixes */
export const appRoutes = [
  { href: "/command", label: "Dashboard" },
  { href: "/systems", label: "AI Systems" },
  { href: "/readiness", label: "Readiness" },
  { href: "/reg-monitor", label: "Reg Monitor" },
  { href: "/approvals", label: "Approvals" },
  { href: "/evidence", label: "Evidence" },
  { href: "/evals", label: "Eval Gates" },
  { href: "/contracts", label: "Contracts" },
  { href: "/audit", label: "Audit Log" },
  { href: "/settings", label: "Workspace" },
  { href: "/public-claims", label: "Public claims" },
] as const;
