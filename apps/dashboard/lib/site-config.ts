const siteUrl = process.env.NEXT_PUBLIC_SITE_URL?.trim() || "https://devernance.space";

/**
 * Public contact addresses on devernance.space, hosted on Zoho Mail. Use these constants everywhere instead of
 * hardcoding an address. Each one is an alias or mailbox listed in docs/EMAIL.md. System addresses (admin, dev,
 * dmarc, no-reply, abuse, postmaster) are not public and do not appear here.
 */
export const contactEmails = {
  hello: "hello@devernance.space",
  sales: "sales@devernance.space",
  demo: "demo@devernance.space",
  support: "support@devernance.space",
  billing: "billing@devernance.space",
  refunds: "refunds@devernance.space",
  privacy: "privacy@devernance.space",
  legal: "legal@devernance.space",
  security: "security@devernance.space",
  partners: "partners@devernance.space",
  press: "press@devernance.space",
  founder: "souravamseekar@devernance.space",
} as const;

export type ContactEmailKey = keyof typeof contactEmails;

/** Ordered list shown on the /contact page. */
export const contactDirectory: readonly { key: ContactEmailKey; label: string; description: string }[] = [
  { key: "hello", label: "General", description: "Questions about Devernance" },
  { key: "sales", label: "Sales", description: "Plans, pricing and readiness sprints" },
  { key: "demo", label: "Demo", description: "Book a walkthrough of the release gate and evidence packs" },
  { key: "support", label: "Support", description: "Help with your workspace, the Action or the API" },
  { key: "billing", label: "Billing", description: "Invoices and payment questions" },
  { key: "refunds", label: "Refunds", description: "Refunds within 14 days of your first payment" },
  { key: "privacy", label: "Privacy and GDPR", description: "Data access, erasure and other data protection requests" },
  { key: "legal", label: "Legal", description: "Contracts, terms and legal notices" },
  { key: "security", label: "Security", description: "Report a vulnerability responsibly" },
  { key: "partners", label: "Partnerships", description: "Integrations, consultancies and resellers" },
  { key: "press", label: "Press", description: "Media enquiries" },
  { key: "founder", label: "Founder", description: "Investors and strategic conversations" },
];

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
  supportEmail: contactEmails.support,
  securityEmail: contactEmails.security,
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
  "/governance",
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
  { path: "/contact", changeFrequency: "monthly" as const, priority: 0.6 },
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
  { href: "/governance", label: "Governance" },
  { href: "/approvals", label: "Approvals" },
  { href: "/evidence", label: "Evidence" },
  { href: "/evals", label: "Eval Gates" },
  { href: "/contracts", label: "Contracts" },
  { href: "/audit", label: "Audit Log" },
  { href: "/settings", label: "Workspace" },
  { href: "/public-claims", label: "Public claims" },
] as const;
