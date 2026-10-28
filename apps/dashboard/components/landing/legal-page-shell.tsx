import type { ReactNode } from "react";

import { MarketingPageShell, PageIntro } from "@/components/landing/marketing-page-shell";
import { siteConfig } from "@/lib/site-config";

type LegalPageShellProps = {
  title: string;
  description: string;
  children: ReactNode;
};

export function LegalPageShell({ title, description, children }: LegalPageShellProps) {
  return (
    <MarketingPageShell>
      <PageIntro
        eyebrow="Legal"
        title={title}
        description={description}
      />
      <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 sm:py-16 lg:px-8">
        <article className="prose-legal max-w-3xl">
          <p className="text-sm text-ink-muted">Last updated: {siteConfig.legalLastUpdated}</p>
          <div className="mt-8 space-y-6 text-[15px] leading-relaxed text-ink/90 [&_a]:text-brand [&_a]:underline [&_a]:underline-offset-2 [&_h2]:text-xl [&_h2]:font-bold [&_h2]:tracking-tight [&_h2]:text-ink">
            {children}
          </div>
        </article>
      </div>
    </MarketingPageShell>
  );
}

/** One titled section of a legal page. */
export function LegalSection({ title, id, children }: { title: string; id?: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-28">
      <h2>{title}</h2>
      <div className="mt-2 space-y-3 [&_ul]:list-disc [&_ul]:space-y-1 [&_ul]:pl-5">{children}</div>
    </section>
  );
}

/** Key/value rows for Common Paper cover pages. */
export function CoverTable({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="divide-y divide-line rounded-xl border border-line bg-white text-sm">
      {rows.map(([k, v]) => (
        <div key={k} className="grid gap-1 px-4 py-3 sm:grid-cols-[13rem_1fr] sm:gap-4">
          <dt className="font-semibold text-ink">{k}</dt>
          <dd className="text-ink/85">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Wraps a legal page with its JSON-LD and the shared shell. */
export function LegalPage({
  title,
  description,
  path,
  children,
}: {
  title: string;
  description: string;
  path: string;
  children: ReactNode;
}) {
  const jsonLd = legalWebPageJsonLd({ name: `${title} — ${siteConfig.name}`, description, path });
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <LegalPageShell title={title} description={description}>
        {children}
      </LegalPageShell>
    </>
  );
}

export function legalWebPageJsonLd(opts: {
  name: string;
  description: string;
  path: string;
}) {
  return {
    "@context": "https://schema.org",
    "@type": "WebPage",
    name: opts.name,
    description: opts.description,
    url: `${siteConfig.url}${opts.path}`,
    isPartOf: {
      "@type": "WebSite",
      name: siteConfig.name,
      url: siteConfig.url,
    },
    inLanguage: "en-GB",
    dateModified: siteConfig.legalLastUpdated,
  };
}
