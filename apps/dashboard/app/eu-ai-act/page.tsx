import Link from "next/link";
import {
  Activity,
  BookOpen,
  CalendarClock,
  ClipboardList,
  ExternalLink,
  FileStack,
  Info,
  Eye,
  FileText,
  ListChecks,
  ShieldAlert,
  UserCheck,
} from "lucide-react";

import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { EuFlag } from "@/components/marketing/brand-icons";
import {
  Band,
  Container,
  IconTile,
  PageHeader,
  PhotoSlot,
  SectionHeading,
  TextLink,
} from "@/components/marketing/primitives";
import { CtaBand, FrameworkList } from "@/components/marketing/sections";
import { DEADLINES, daysUntil, deadlineStatus, formatDeadlineDate } from "@/lib/deadlines";
import { PROVISIONS } from "@/lib/eu-ai-act-provisions";
import { marketingMedia } from "@/lib/marketing-media";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";
import { cn } from "@/lib/utils";

const title = "EU AI Act";
const description =
  "From obligations to operational controls. Devernance helps you put the EU AI Act into practice with evidence, not paperwork — risk classification, obligation mapping, human oversight and Article 50 transparency.";
const path = "/eu-ai-act";

// Deadline status ("applies now" / "in N days") is computed at build time; rebuild daily.
export const revalidate = 86400;

export const metadata = marketingMetadata({
  title: "EU AI Act readiness software",
  description,
  path,
  keywords: ["EU AI Act", "Article 50", "Annex III", "Digital Omnibus on AI", "EU AI Act deadlines"],
});

const capabilities = [
  { icon: ShieldAlert, title: "Risk classification", body: "Map to prohibited, high, limited and minimal risk tiers, with rationale." },
  { icon: ListChecks, title: "Obligations mapping", body: "Identify applicable articles and requirements from a pinned corpus." },
  { icon: ClipboardList, title: "Controls implementation", body: "Turn legal text into technical and organisational controls you can gate on." },
  { icon: FileText, title: "Evidence and documentation", body: "Maintain records with citations, attached to each system." },
  { icon: UserCheck, title: "Human oversight", body: "Support accountability with documented, meaningful human review." },
  { icon: Eye, title: "Transparency (Article 50)", body: "Track disclosure and content-marking duties that already apply." },
  { icon: Activity, title: "Ongoing monitoring", body: "Re-check every release in CI, so the record stays current as systems change." },
];

const sourceLinks = [
  { icon: BookOpen, label: "Official EU text", href: "https://eur-lex.europa.eu/eli/reg/2024/1689/oj", external: true },
  { icon: FileStack, label: "Digital Omnibus on AI", href: "https://eur-lex.europa.eu/eli/reg/2026/1744/oj", external: true },
  { icon: CalendarClock, label: "Key deadlines", href: "#deadlines", external: false },
  { icon: ListChecks, label: "Our methodology", href: "/method", external: false },
];


export default function EuAiActPage() {
  const today = new Date();
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
          <PageHeader
            tone="light"
            eyebrow="EU AI Act"
            title="Built for the EU AI Act."
            description="From obligations to operational controls. Devernance helps you put the EU AI Act into practice with evidence, not paperwork."
          />
          <div className="mt-12 grid gap-10 lg:grid-cols-[minmax(0,0.7fr)_minmax(0,1.3fr)] lg:gap-14">
            <div className="space-y-6">
              <PhotoSlot
                slot={marketingMedia.euFlag}
                className="aspect-[4/5] rounded-2xl shadow-lg shadow-navy-900/10"
                sizes="(min-width: 1024px) 30vw, 100vw"
                fallback={
                  <div
                    aria-hidden="true"
                    className="absolute inset-0 flex items-center justify-center bg-gradient-to-b from-[#3d6be0] via-[#1d3fbf] to-navy-900"
                  >
                    <EuFlag className="w-3/5 drop-shadow-2xl" />
                  </div>
                }
              />
              <div className="rounded-2xl border border-line bg-white p-6">
                <p className="font-semibold text-ink">EU AI Act</p>
                <p className="mt-1 text-sm text-ink-muted">
                  Regulation (EU) 2024/1689
                  <br />
                  Amended by Regulation (EU) 2026/1744
                </p>
                <ul className="mt-4 space-y-2.5">
                  {sourceLinks.map((l) => (
                    <li key={l.label}>
                      <a
                        href={l.href}
                        {...(l.external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
                        className="inline-flex items-center gap-2.5 text-sm font-medium text-brand hover:underline"
                      >
                        <l.icon className="h-4 w-4" aria-hidden="true" />
                        {l.label}
                        {l.external ? <ExternalLink className="h-3 w-3 opacity-60" aria-label="(opens in a new tab)" /> : null}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
            <div>
              <ul className="grid content-start gap-7">
                {capabilities.map((c) => (
                  <li key={c.title} className="flex gap-4">
                    <IconTile icon={c.icon} className="bg-white text-brand shadow-sm ring-1 ring-brand/15" />
                    <div>
                      <h2 className="font-semibold text-ink">{c.title}</h2>
                      <p className="mt-1 text-sm text-ink-muted">{c.body}</p>
                    </div>
                  </li>
                ))}
              </ul>
              <div className="mt-10 flex gap-3 rounded-2xl border border-brand/15 bg-brand-soft p-6">
                <Info className="mt-0.5 h-5 w-5 shrink-0 text-brand" aria-hidden="true" />
                <div className="text-sm">
                  <p className="font-semibold text-ink">We do not provide legal advice or certification.</p>
                  <p className="mt-1 text-ink-muted">
                    Devernance helps you put obligations into practice through evidence, workflows and release gates.
                    Your counsel makes the legal determination.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </Container>
      </Band>

      <Band tone="light" id="deadlines" className="scroll-mt-24">
        <Container className="py-20 sm:py-24">
          <SectionHeading
            tone="light"
            eyebrow="Key deadlines"
            title="Dates that matter for AI features."
            description="Each date links to its source. Dates reflect the Digital Omnibus on AI."
          />
          <ol className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
            {DEADLINES.filter((d) => d.region === "EU").map((d) => {
              const now = deadlineStatus(d, today) === "applies-now";
              return (
                <li key={d.id} className="flex flex-col rounded-2xl border border-line bg-white p-5">
                  <span
                    className={cn(
                      "self-start rounded-full px-2.5 py-0.5 text-xs font-semibold",
                      now ? "bg-review-soft text-review" : "bg-brand-soft text-brand",
                    )}
                  >
                    {now ? "Applies now" : `In ${daysUntil(d, today)} days`}
                  </span>
                  <p className="mt-4 text-2xl font-bold tracking-tight text-ink">{formatDeadlineDate(d.date)}</p>
                  <p className="mt-1 flex-1 text-sm text-ink/85">{d.label}</p>
                  <a
                    href={d.source.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-4 text-xs font-medium text-brand hover:underline"
                  >
                    {d.source.title}
                  </a>
                </li>
              );
            })}
          </ol>
        </Container>
      </Band>

      <Band tone="light" muted id="articles" className="scroll-mt-24">
        <Container className="py-20 sm:py-24">
          <SectionHeading
            tone="light"
            eyebrow="Article guides"
            title="The provisions AI teams ask about most."
            description="Each guide quotes the official text, says when it applies, and lists the evidence that usually supports it."
          />
          <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {PROVISIONS.map((p) => (
              <li key={p.slug}>
                <Link
                  href={`/eu-ai-act/${p.slug}`}
                  className="flex h-full flex-col rounded-2xl border border-line bg-white p-5 transition-colors hover:border-brand"
                >
                  <span className="text-sm font-semibold text-brand">{p.label}</span>
                  <span className="mt-1 flex-1 text-sm font-medium text-ink">{p.title}</span>
                  <span className="mt-3 text-xs text-ink-muted">
                    {p.forceStatus === "IN_FORCE" ? "Applies now" : `From ${formatDeadlineDate(p.forceFrom)}`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Container>
      </Band>

      <Band tone="light" id="sources" className="scroll-mt-24">
        <Container className="py-20 sm:py-24">
          <div className="grid gap-10 lg:grid-cols-[1fr_1.2fr]">
            <div>
              <SectionHeading
                tone="light"
                eyebrow="Regulatory sources"
                title="What is live, and what is coming."
                description="Framework coverage only says live when the feature ships. We provide evidence and readiness tooling, not legal advice or certification."
              />
              <div className="mt-8">
                <TextLink href="/method" tone="light">
                  View methodology
                </TextLink>
              </div>
            </div>
            <div className="rounded-2xl border border-line bg-white p-6">
              <h3 className="font-semibold text-ink">Regulatory sources</h3>
              <div className="mt-2">
                <FrameworkList />
              </div>
            </div>
          </div>
        </Container>
      </Band>

      <CtaBand />
    </MarketingPageShell>
  );
}
