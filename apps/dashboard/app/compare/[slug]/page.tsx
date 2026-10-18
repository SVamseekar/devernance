import { notFound } from "next/navigation";
import { ExternalLink } from "lucide-react";

import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { Band, CheckDot, Container, PageHeader } from "@/components/marketing/primitives";
import { CtaBand } from "@/components/marketing/sections";
import { COMPARE_PAGES, getComparePage, type CompareSource } from "@/content/compare";
import { formatDeadlineDate } from "@/lib/deadlines";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

type Params = { params: Promise<{ slug: string }> };

export const dynamicParams = false;

export function generateStaticParams() {
  return COMPARE_PAGES.map((p) => ({ slug: p.slug }));
}

export async function generateMetadata({ params }: Params) {
  const page = getComparePage((await params).slug);
  if (!page) return {};
  return marketingMetadata({ title: page.title, description: page.summary, path: `/compare/${page.slug}` });
}

export default async function ComparePage({ params }: Params) {
  const page = getComparePage((await params).slug);
  if (!page) notFound();
  const path = `/compare/${page.slug}`;
  const sources = [...new Map(page.rows.flatMap((r) => (r.source ? [[r.source.url, r.source]] : []))).values()];
  const checked = formatDeadlineDate(page.retrieved);

  return (
    <MarketingPageShell
      jsonLd={webPageJsonLd({
        name: `${page.title} — ${siteConfig.shortName}`,
        description: page.summary,
        path,
        crumbs: [
          { name: "Home", path: "/" },
          { name: page.title, path },
        ],
      })}
    >
      <Band tone="light" muted>
        <Container className="pt-10 pb-20 sm:pt-12">
          <PageHeader tone="light" eyebrow="Compare" title={page.title} description={page.summary} />
          <div className="mt-6 max-w-3xl space-y-4 text-ink/85">
            {page.intro.map((p) => (
              <p key={p}>{p}</p>
            ))}
          </div>

          <div className="mt-10 overflow-x-auto rounded-2xl border border-line bg-white">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="bg-mist text-ink-muted">
                <tr>
                  <th scope="col" className="w-40 px-4 py-3 font-semibold">
                    Aspect
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold text-brand">
                    {siteConfig.shortName}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {page.themLabel}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {page.rows.map((row) => (
                  <tr key={row.aspect} className="align-top">
                    <th scope="row" className="px-4 py-4 font-semibold text-ink">
                      {row.aspect}
                    </th>
                    <td className="px-4 py-4 text-ink/85">{row.us}</td>
                    <td className="px-4 py-4 text-ink/85">
                      {row.them}
                      {row.source ? <SourceLink source={row.source} /> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h2 className="mt-16 text-2xl font-bold tracking-tight text-ink">Who should choose which</h2>
          <div className="mt-6 grid gap-6 md:grid-cols-2">
            <ChoiceCard title={`Choose ${siteConfig.shortName} if`} items={page.chooseUs} />
            <ChoiceCard title={`Choose ${page.themChoice} if`} items={page.chooseThem} />
          </div>

          <div className="mt-16 max-w-3xl text-sm text-ink-muted">
            {sources.length ? (
              <>
                <h2 className="text-base font-semibold text-ink">Sources</h2>
                <ul className="mt-3 space-y-1.5">
                  {sources.map((s) => (
                    <li key={s.url}>
                      <a href={s.url} rel="noopener noreferrer" target="_blank" className="text-brand hover:underline">
                        {s.title}
                      </a>{" "}
                      (retrieved {checked})
                    </li>
                  ))}
                </ul>
              </>
            ) : null}
            <p className="mt-4">
              Last checked {checked}. Facts about other products come only from their own public pages and may have
              changed since. Tell us at{" "}
              <a href={`mailto:${siteConfig.supportEmail}`} className="text-brand hover:underline">
                {siteConfig.supportEmail}
              </a>{" "}
              if something is out of date. Devernance provides evidence and readiness tooling, not legal advice.
            </p>
          </div>
        </Container>
      </Band>
      <CtaBand />
    </MarketingPageShell>
  );
}

function SourceLink({ source }: { source: CompareSource }) {
  return (
    <a
      href={source.url}
      rel="noopener noreferrer"
      target="_blank"
      className="mt-1.5 flex items-center gap-1 text-xs text-ink-muted hover:text-brand"
    >
      Source: {source.title}
      <ExternalLink className="h-3 w-3" aria-hidden="true" />
    </a>
  );
}

function ChoiceCard({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="rounded-2xl border border-line bg-white p-6">
      <h3 className="font-semibold text-ink">{title}</h3>
      <ul className="mt-4 space-y-3">
        {items.map((item) => (
          <li key={item} className="flex items-start gap-3 text-sm text-ink/85">
            <CheckDot className="mt-0.5" />
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}
