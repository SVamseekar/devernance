/**
 * Third parties that process personal data for Devernance. Used by /subprocessors and the DPA cover page.
 * lib/subprocessors.test.ts fails when the app configures a provider that is missing here.
 */
export interface Subprocessor {
  name: string;
  purpose: string;
  location: string;
  dataCategories: string;
  link: string;
}

export const SUBPROCESSORS: Subprocessor[] = [
  {
    name: "Oracle Cloud Infrastructure",
    purpose: "Hosting (compute, storage) and fallback transactional email",
    location: "EU (Frankfurt/Amsterdam)",
    dataCategories: "All workspace data; email address and email content",
    link: "https://www.oracle.com/cloud/",
  },
  {
    name: "Cloudflare",
    purpose: "CDN, TLS, tunnel, bot protection (Turnstile), cookieless web analytics, encrypted backups (R2, EU jurisdiction)",
    location: "Global edge; R2 in the EU",
    dataCategories: "Traffic metadata; encrypted backups",
    link: "https://www.cloudflare.com/",
  },
  {
    name: "Vercel",
    purpose: "Hosting for the web app and its API proxy",
    location: "Global edge; see provider",
    dataCategories: "Traffic metadata (IP address, request headers); workspace data in transit through the proxy",
    link: "https://vercel.com/legal/privacy-policy",
  },
  {
    name: "Google Analytics",
    purpose: "Website usage analytics, only for visitors who accept analytics cookies",
    location: "US (EU-US Data Privacy Framework)",
    dataCategories: "Pseudonymous cookie identifier, pages viewed, device and browser data, approximate location",
    link: "https://policies.google.com/privacy",
  },
  {
    name: "Dodo Payments",
    purpose: "Merchant of record: checkout, tax, invoices",
    location: "See provider",
    dataCategories: "Billing contact and payment details (we never see card data)",
    link: "https://dodopayments.com/",
  },
  {
    name: "Resend",
    purpose: "Transactional email",
    location: "See provider",
    dataCategories: "Email address, email content",
    link: "https://resend.com/",
  },
  {
    name: "Sentry",
    purpose: "Error monitoring (no request bodies)",
    location: "See provider",
    dataCategories: "Technical error data",
    link: "https://sentry.io/",
  },
  {
    name: "Discord",
    purpose: "Internal notification of demo requests",
    location: "See provider",
    dataCategories: "Demo request details: work email, company, role, message",
    link: "https://discord.com/",
  },
  {
    name: "Google / Microsoft",
    purpose: "Optional sign-in",
    location: "See provider",
    dataCategories: "Name, email, identity token",
    link: "https://policies.google.com/",
  },
];
