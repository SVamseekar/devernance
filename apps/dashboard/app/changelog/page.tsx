import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { Band, CheckDot, Container, PageHeader } from "@/components/marketing/primitives";
import { CtaBand } from "@/components/marketing/sections";
import { loadChangelog } from "@/lib/changelog";
import { formatDeadlineDate } from "@/lib/deadlines";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

const title = "Changelog";
const description = "What changed in Devernance, newest first. One entry per release of the hosted product.";
const path = "/changelog";

export const metadata = marketingMetadata({ title, description, path });

export default function ChangelogPage() {
  const entries = loadChangelog();
  return (
    <MarketingPageShell
      jsonLd={webPageJsonLd({
        name: `${title} — ${siteConfig.shortName}`,
        description,
        path,
        crumbs: [
          { name: "Home", path: "/" },
          { name: title, path },
        ],
      })}
    >
      <Band tone="light" muted>
        <Container className="pt-10 pb-20 sm:pt-12">
          <PageHeader tone="light" eyebrow="Updates" title="Changelog." description={description} />
          <ol className="mt-12 max-w-3xl space-y-6">
            {entries.map((entry) => (
              <li key={entry.date} id={entry.date} className="scroll-mt-28 rounded-2xl border border-line bg-white p-6 sm:p-8">
                <time dateTime={entry.date} className="text-sm font-semibold text-brand">
                  {formatDeadlineDate(entry.date)}
                </time>
                <h2 className="mt-1 text-xl font-bold tracking-tight text-ink">{entry.title}</h2>
                <ul className="mt-4 space-y-2.5">
                  {entry.items.map((item) => (
                    <li key={item} className="flex items-start gap-3 text-ink/85">
                      <CheckDot className="mt-0.5" />
                      {item}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ol>
        </Container>
      </Band>
      <CtaBand />
    </MarketingPageShell>
  );
}
