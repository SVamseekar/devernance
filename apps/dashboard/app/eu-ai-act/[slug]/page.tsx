import Link from "next/link";
import { notFound } from "next/navigation";
import { ExternalLink } from "lucide-react";

import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { Band, CheckDot, Container, PageHeader } from "@/components/marketing/primitives";
import { CtaBand } from "@/components/marketing/sections";
import { formatDeadlineDate } from "@/lib/deadlines";
import { CORPUS_ATTRIBUTION, PROVISIONS, PROVISION_GUIDES, provisionBySlug } from "@/lib/eu-ai-act-provisions";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";
import { cn } from "@/lib/utils";

type Params = { params: Promise<{ slug: string }> };

export const dynamicParams = false;

export function generateStaticParams() {
  return PROVISIONS.map((p) => ({ slug: p.slug }));
}

function heading(p: { label: string; title: string }) {
  return `EU AI Act ${p.label}: ${p.title}`;
}

function summary(p: { label: string; title: string; forceFrom: string }) {
  return `${p.label} of the EU AI Act (${p.title}): the official text, when it applies (${formatDeadlineDate(p.forceFrom)}), and the evidence that usually supports it.`;
}

export async function generateMetadata({ params }: Params) {
  const p = provisionBySlug((await params).slug);
  if (!p) return {};
  return marketingMetadata({
    title: heading(p),
    description: summary(p),
    path: `/eu-ai-act/${p.slug}`,
    keywords: [`EU AI Act ${p.label}`, p.title, `AI Act ${p.label} evidence`],
  });
}

export default async function ProvisionPage({ params }: Params) {
  const p = provisionBySlug((await params).slug);
  if (!p) notFound();
  const guide = PROVISION_GUIDES[p.slug];
  const path = `/eu-ai-act/${p.slug}`;
  const inForce = p.forceStatus === "IN_FORCE";
  const related = PROVISIONS.filter((x) => x.slug !== p.slug);

  return (
    <MarketingPageShell
      jsonLd={webPageJsonLd({
        name: heading(p),
        description: summary(p),
        path,
        crumbs: [
          { name: "Home", path: "/" },
          { name: "EU AI Act", path: "/eu-ai-act" },
          { name: p.label, path },
        ],
      })}
    >
      <Band tone="light" muted>
        <Container className="pt-10 pb-20 sm:pt-12">
          <PageHeader
            tone="light"
            eyebrow="EU AI Act"
            title={heading(p)}
            crumbs={[
              { label: "EU AI Act", href: "/eu-ai-act" },
              { label: p.label, href: path },
            ]}
          />
          <p className="mt-5 flex flex-wrap items-center gap-2 text-sm">
            <span
              className={cn(
                "rounded-full px-2.5 py-0.5 text-xs font-semibold",
                inForce ? "bg-pass-soft text-pass" : "bg-review-soft text-review",
              )}
            >
              {inForce ? "Applies now" : "Upcoming"}
            </span>
            <span className="text-ink/85">
              Applies from {formatDeadlineDate(p.forceFrom)}
              {p.scopeNote ? `. ${p.scopeNote}.` : "."}
            </span>
          </p>

          <div className="mt-10 grid gap-10 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
            <div className="space-y-10">
              <figure className="rounded-2xl border border-line bg-white p-6 sm:p-8">
                <blockquote className="border-l-4 border-brand pl-5 text-lg leading-relaxed text-ink">
                  {p.excerpt}
                </blockquote>
                <figcaption className="mt-5 space-y-1 text-xs text-ink-muted">
                  <p>
                    {p.label}
                    {p.slug.startsWith("article-") ? ", paragraph 1" : ", opening"}. Regulation (EU) 2024/1689,
                    consolidated text of 27 July 2026.{" "}
                    <a
                      href={p.sourceUrl}
                      rel="noopener noreferrer"
                      target="_blank"
                      className="inline-flex items-center gap-1 text-brand hover:underline"
                    >
                      Read the full text on EUR-Lex
                      <ExternalLink className="h-3 w-3" aria-hidden="true" />
                    </a>
                  </p>
                  <p>{CORPUS_ATTRIBUTION}</p>
                </figcaption>
              </figure>

              <section>
                <h2 className="text-2xl font-bold tracking-tight text-ink">What evidence usually supports this</h2>
                <ul className="mt-5 space-y-3">
                  {guide.evidence.map((item) => (
                    <li key={item} className="flex items-start gap-3 text-ink/85">
                      <CheckDot className="mt-0.5" />
                      {item}
                    </li>
                  ))}
                </ul>
                {guide.controls.length ? (
                  <p className="mt-5 text-sm text-ink-muted">
                    Maps to the{" "}
                    {guide.controls.map((code, i) => (
                      <span key={code}>
                        {i ? " and " : ""}
                        <code className="rounded bg-mist px-1.5 py-0.5 font-mono text-xs text-ink">{code}</code>
                      </span>
                    ))}{" "}
                    {guide.controls.length > 1 ? "controls" : "control"} in the Devernance catalog.
                  </p>
                ) : null}
              </section>

              <section>
                <h2 className="text-2xl font-bold tracking-tight text-ink">How Devernance gates it</h2>
                <p className="mt-4 text-ink/85">{guide.gate}</p>
              </section>

              <p className="rounded-xl border border-line bg-white p-4 text-sm text-ink-muted">
                This page shows an excerpt of the law and the evidence teams commonly keep for it. It is not legal advice
                and does not say whether your system complies. Read the full text and take legal advice for your case.
              </p>
            </div>

            <aside className="space-y-6">
              <div className="rounded-2xl border border-line bg-white p-6">
                <h2 className="font-semibold text-ink">Does this apply to your system?</h2>
                <p className="mt-2 text-sm text-ink-muted">
                  Answer a short questionnaire. No sign-in, nothing stored.
                </p>
                <Link
                  href="/tools/ai-act-check"
                  className="mt-4 inline-flex text-sm font-semibold text-brand hover:underline"
                >
                  Run the free AI Act check
                </Link>
              </div>
              <nav aria-label="Other provisions" className="rounded-2xl border border-line bg-white p-6">
                <h2 className="font-semibold text-ink">Other provisions</h2>
                <ul className="mt-3 space-y-2 text-sm">
                  {related.map((x) => (
                    <li key={x.slug}>
                      <Link href={`/eu-ai-act/${x.slug}`} className="text-ink/85 hover:text-brand">
                        <span className="font-medium text-ink">{x.label}</span> · {x.title}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
            </aside>
          </div>
        </Container>
      </Band>
      <CtaBand
        title={`Gate ${p.label} evidence in CI.`}
        description={`Free plan, no card. ${siteConfig.shortName} checks the evidence on every release and hands buyers a signed pack.`}
      />
    </MarketingPageShell>
  );
}
