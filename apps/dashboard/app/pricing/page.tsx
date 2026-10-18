import Link from "next/link";

import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { PricingTable } from "@/components/marketing/pricing-table";
import { Band, Container, PageHeader, SectionHeading } from "@/components/marketing/primitives";
import { CtaBand, TrustBadges } from "@/components/marketing/sections";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

const title = "Pricing";
const description =
  "Start free with one gated AI system. Team and Business plans add systems, editors and signed evidence packs. Enterprise on request.";
const path = "/pricing";

export const metadata = marketingMetadata({
  title,
  description,
  path,
  keywords: ["AI governance pricing", "EU AI Act software pricing", "AI release gate pricing"],
});

const faqs = [
  { q: "Is this legal advice?", a: "No. Devernance provides evidence and readiness tooling. Your counsel decides how the law applies." },
  { q: "Do you certify compliance?", a: "No. We report evidence and readiness; notified bodies and your counsel make those determinations." },
  {
    q: "Where is my data stored?",
    a: "In the EU, on Oracle Cloud in Frankfurt and Amsterdam. Backups are encrypted and kept in an EU-jurisdiction bucket.",
  },
  { q: "Do you use my data to train AI?", a: "No. No LLM processes your content by default, and we never train models on it." },
  { q: "Who is the seller?", a: "Dodo Payments is the merchant of record and handles tax and invoices." },
  {
    q: "Can I add a VAT or tax ID?",
    a: "Yes. Enter it at checkout; Dodo Payments shows it on your invoices and applies reverse charge where it can.",
  },
  { q: "Can I cancel?", a: "Any time in the customer portal. Access continues to the end of the paid period." },
  { q: "Refunds?", a: "Within 14 days of your first payment on any plan. See the refund policy." },
  { q: "What counts as a gated system?", a: "An AI system registered in Devernance that you gate in CI. Viewers are free." },
  { q: "What happens if I downgrade?", a: "Nothing is deleted. Systems beyond your plan become read-only." },
];

/** Launch offer; the banner hides itself after this date. */
const FOUNDING_OFFER_ENDS = new Date("2026-11-25T00:00:00Z");

// The founding banner depends on today's date; rebuild hourly.
export const revalidate = 3600;

export default function PricingPage() {
  return (
    <MarketingPageShell
      jsonLd={[
        {
          "@context": "https://schema.org",
          "@type": "FAQPage",
          mainEntity: faqs.map((f) => ({ "@type": "Question", name: f.q, acceptedAnswer: { "@type": "Answer", text: f.a } })),
        },
        webPageJsonLd({
        name: `${title} — ${siteConfig.shortName}`,
        description,
        path,
        crumbs: [
          { name: "Home", path: "/" },
          { name: title, path },
        ],
      }),
      ]}
    >
      <Band tone="light">
        <Container className="pt-10 pb-20 sm:pt-12">
          {Date.now() < FOUNDING_OFFER_ENDS.getTime() ? (
            <p className="mb-8 flex flex-wrap items-center gap-2 rounded-xl border border-brand/20 bg-brand-soft px-4 py-3 text-sm text-ink">
              <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-white">
                Founding offer
              </span>
              Founding customers: 40% off yearly plans with code <strong className="font-mono">FOUNDING40</strong>, first 50
              teams.
            </p>
          ) : null}
          <PricingTable
            header={
              <PageHeader
                tone="light"
                eyebrow="Pricing"
                title="Simple, transparent pricing."
                description="Start free, scale as you grow. No hidden fees."
              />
            }
          />
          <p className="mt-6 text-sm text-ink-muted">
            Prices in USD, excluding tax. Features listed as coming soon are not included until they ship.
          </p>
        </Container>
      </Band>

      <Band tone="light" muted className="border-y border-line">
        <Container className="py-12">
          <TrustBadges />
        </Container>
      </Band>

      <Band tone="light">
        <Container className="py-20 sm:py-24">
          <SectionHeading tone="light" eyebrow="FAQ" title="Questions about plans." />
          <dl className="mt-10 grid gap-x-10 gap-y-8 md:grid-cols-2">
            {faqs.map((f) => (
              <div key={f.q}>
                <dt className="font-semibold text-ink">{f.q}</dt>
                <dd className="mt-2 text-ink-muted">
                  {f.a}
                  {f.q === "Refunds?" ? (
                    <>
                      {" "}
                      <Link href="/refunds" className="font-semibold text-brand hover:underline">
                        Refund policy
                      </Link>
                    </>
                  ) : null}
                </dd>
              </div>
            ))}
          </dl>
        </Container>
      </Band>

      <CtaBand />
    </MarketingPageShell>
  );
}
