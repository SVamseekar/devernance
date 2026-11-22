import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { Band, Container, PageHeader } from "@/components/marketing/primitives";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { contactDirectory, contactEmails, siteConfig } from "@/lib/site-config";

const title = "Contact";
const description = `Who to write to at ${siteConfig.shortName}: sales, support, billing, privacy, legal, security, partnerships and press.`;
const path = "/contact";

export const metadata = marketingMetadata({
  title,
  description,
  path,
  keywords: ["Devernance contact", "Devernance support", "report a vulnerability"],
});

export default function ContactPage() {
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
      <Band tone="light">
        <Container className="pt-10 pb-20 sm:pt-12 sm:pb-24">
          <PageHeader
            tone="light"
            eyebrow="Contact"
            title="Write to the right inbox."
            description="Every address below reaches a person. We reply on business days."
            className="max-w-5xl"
          />
          <dl className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {contactDirectory.map(({ key, label, description: what }) => (
              <div key={key} className="rounded-xl border border-line bg-white p-5">
                <dt className="text-sm font-semibold text-ink">{label}</dt>
                <dd className="mt-1 text-sm text-ink-muted">{what}</dd>
                <dd className="mt-3 text-sm">
                  <a href={`mailto:${contactEmails[key]}`} className="font-medium text-brand hover:underline">
                    {contactEmails[key]}
                  </a>
                </dd>
              </div>
            ))}
          </dl>
          <p className="mt-8 max-w-3xl text-sm text-ink-muted">
            Security reports follow our <a href="/security" className="text-brand underline underline-offset-2">disclosure policy</a>.
            Prefer a walkthrough? <a href="/request-demo" className="text-brand underline underline-offset-2">Request a demo</a>.
          </p>
        </Container>
      </Band>
    </MarketingPageShell>
  );
}
