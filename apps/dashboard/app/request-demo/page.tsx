import { DemoRequestForm } from "@/components/landing/demo-request-form";
import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { DemoButton } from "@/components/marketing/demo-button";
import { Band, CheckDot, Container, PageHeader } from "@/components/marketing/primitives";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

const title = "Request a demo";
const description =
  "Walk through a release decision on one of your own AI systems with the team that builds Devernance. For Enterprise plans, order forms and security reviews.";
const path = "/request-demo";

export const metadata = marketingMetadata({
  title,
  description,
  path,
  keywords: ["EU AI Act demo", "AI governance demo", "AI release gate demo"],
});

const promises = [
  "Personalised walkthrough",
  "Your specific requirements",
  "Technical and compliance discussion",
  "No commitment",
];

export default function RequestDemoPage() {
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
        <Container className="pt-10 pb-24 sm:pt-12">
          <div className="grid items-start gap-12 lg:grid-cols-2 lg:gap-16">
            <div>
              <PageHeader
                tone="light"
                eyebrow="Request a demo"
                title="Let's walk through your use case."
                description={`See how ${siteConfig.shortName} can help you ship AI features with confidence.`}
              />
              <ul className="mt-8 space-y-3">
                {promises.map((p) => (
                  <li key={p} className="flex items-center gap-3 text-ink/85">
                    <CheckDot className="h-6 w-6 [&>svg]:h-3.5 [&>svg]:w-3.5" />
                    {p}
                  </li>
                ))}
              </ul>
              <div className="mt-10 rounded-2xl border border-line bg-white p-5">
                <p className="text-sm font-semibold text-ink">Prefer to look first?</p>
                <p className="mt-1 text-sm text-ink-muted">
                  Open the read-only demo workspace now, no sign-up needed.
                </p>
                <div className="mt-4">
                  <DemoButton variant="outline" />
                </div>
              </div>
            </div>
            <DemoRequestForm />
          </div>
        </Container>
      </Band>
    </MarketingPageShell>
  );
}
