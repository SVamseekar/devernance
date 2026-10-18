import { BookOpen, CheckCircle2, ListChecks, ScanSearch, SlidersHorizontal, UserCheck } from "lucide-react";

import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { Band, Container, IconTile, PageHeader, SectionHeading } from "@/components/marketing/primitives";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

const title = "Methodology";
const description =
  "How Devernance maps AI systems to the EU AI Act: a pinned legal corpus, mappings a person accepts, control modes for provisions in force, and checks that fail closed when evidence is missing.";
const path = "/method";

export const metadata = marketingMetadata({
  title: "Methodology — how we map and interpret",
  description,
  path,
  keywords: ["EU AI Act methodology", "pinned legal corpus", "fail-closed release gate", "AI control mapping"],
});

const principles = [
  {
    icon: BookOpen,
    title: "A pinned legal corpus",
    body: "Systems are read against a fixed, versioned copy of the legal text from EUR-Lex, including the Digital Omnibus on AI. The pin is the text the classification reads, so a result can be reproduced later.",
  },
  {
    icon: ListChecks,
    title: "Mappings are proposals",
    body: "Links between a system and the obligations that may apply arrive in a queue. A person accepts or rejects each one. Nothing is applied automatically.",
  },
  {
    icon: SlidersHorizontal,
    title: "Control modes",
    body: "Each control on a provision in force has a mode: INFORMATIONAL, WARNING, APPROVAL_REQUIRED or BLOCKING. The mode decides whether a gap warns, waits for a person, or stops the release.",
  },
  {
    icon: ScanSearch,
    title: "Fail closed",
    body: "Missing evidence, a failed evaluation or an open data-contract breach never passes. If the decision cannot be read, the gate returns BLOCKED.",
  },
  {
    icon: UserCheck,
    title: "People decide",
    body: "Risk class is never changed automatically. Engineering, compliance and legal sign off, and every decision is written to a hash-chained ledger.",
  },
  {
    icon: CheckCircle2,
    title: "Checkable outside the platform",
    body: "Evidence exports can be scanned with the open-source evgraph-cli. The evidence pack and the scan report the same gaps.",
  },
];

const limits = [
  "We do not invent a missing approval timestamp or dataset licence. The finding stays inconclusive.",
  "We do not decide whether a system meets the law. Counsel makes that determination.",
  "We do not certify systems or act as a notified body.",
  "We do not send your content to a language model by default.",
];

export default function MethodPage() {
  return (
    <MarketingPageShell
      jsonLd={webPageJsonLd({
        name: `${title} — ${siteConfig.name}`,
        description,
        path,
        crumbs: [
          { name: "Home", path: "/" },
          { name: title, path },
        ],
      })}
    >
      <Band tone="light" muted className="border-b border-line">
        <Container className="pt-10 pb-12 sm:pt-12 sm:pb-16">
          <PageHeader
            tone="light"
            eyebrow="Methodology"
            title="How we map and interpret."
            description="Evidence and readiness you can reproduce: a pinned corpus, mappings a person accepts, and checks that fail closed."
          />
        </Container>
      </Band>

      <Band tone="light">
        <Container className="py-20 sm:py-24">
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {principles.map((p) => (
              <li key={p.title} className="rounded-2xl border border-line bg-white p-6">
                <IconTile icon={p.icon} className="bg-brand-soft text-brand" />
                <h2 className="mt-5 text-lg font-semibold text-ink">{p.title}</h2>
                <p className="mt-2 text-sm text-ink-muted">{p.body}</p>
              </li>
            ))}
          </ul>
        </Container>
      </Band>

      <Band tone="light" muted className="border-t border-line">
        <Container className="py-20 sm:py-24">
          <SectionHeading tone="light" eyebrow="Limits" title="What we do not infer." />
          <ul className="mt-8 grid gap-3 sm:grid-cols-2">
            {limits.map((item) => (
              <li key={item} className="rounded-xl border border-line bg-white px-4 py-3 text-sm text-ink/85">
                {item}
              </li>
            ))}
          </ul>
        </Container>
      </Band>

    </MarketingPageShell>
  );
}
