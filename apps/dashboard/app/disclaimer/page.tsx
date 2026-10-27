import type { Metadata } from "next";

import {
  LegalPageShell,
  legalWebPageJsonLd,
} from "@/components/landing/legal-page-shell";
import { siteConfig } from "@/lib/site-config";

const title = "Disclaimer";
const description =
  "Important limitations: Devernance is not legal advice, not a notified body, and does not issue EU AI Act certifications.";

export const metadata: Metadata = {
  title,
  description,
  alternates: { canonical: "/disclaimer" },
  openGraph: {
    title: `${title} — ${siteConfig.name}`,
    description,
    url: `${siteConfig.url}/disclaimer`,
    type: "website",
  },
};

export default function DisclaimerPage() {
  const jsonLd = legalWebPageJsonLd({
    name: `${title} — ${siteConfig.name}`,
    description,
    path: "/disclaimer",
  });

  return (
    <>
      <script
        type="application/ld+json"
        // eslint-disable-next-line react/no-danger
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <LegalPageShell title={title} description={description}>
        <section>
          <p>
            {siteConfig.name} assists organisations with AI system governance:
            registry, risk classification workflows, cited evidence retrieval,
            evaluation gates, data-contract monitoring, approvals, certification{" "}
            <em>readiness</em> scoring, and related audit artefacts.
          </p>
        </section>

        <section>
          <h2 className="font-heading text-lg font-semibold">
            Not legal advice or certification
          </h2>
          <ul className="mt-2 list-disc space-y-2 pl-5">
            <li>
              The product does <strong>not</strong> provide legal advice or replace
              qualified counsel on the EU AI Act or related law.
            </li>
            <li>
              It is <strong>not</strong> a notified body and does{" "}
              <strong>not</strong> issue certificates, CE marks, or official
              conformity assessments.
            </li>
            <li>
              Assisted obligation maps and risk workflows are decision-support
              tools — they are <strong>not</strong> a final legal determination of
              your obligations.
            </li>
            <li>
              Certification readiness scores and gap lists measure documentation
              readiness for human review only — never a claim that you are
              &quot;certified.&quot;
            </li>
          </ul>
        </section>

        <section>
          <h2 className="font-heading text-lg font-semibold">Outputs &amp; data</h2>
          <p className="mt-2">
            Release decisions (PASS / REVIEW / BLOCKED), eval scores, and evidence
            answers depend on the data, thresholds, and configuration your
            organisation supplies. Incorrect or incomplete inputs can produce
            incomplete or misleading outputs. Regulatory change feeds may lag
            official publications.
          </p>
        </section>

        <section>
          <h2 className="font-heading text-lg font-semibold">Contact</h2>
          <p className="mt-2">
            Questions about this disclaimer:{" "}
            <a
              href={`mailto:${siteConfig.supportEmail}`}
              className="text-primary underline underline-offset-2"
            >
              {siteConfig.supportEmail}
            </a>
            .
          </p>
        </section>
      </LegalPageShell>
    </>
  );
}
