import type { Metadata } from "next";
import Link from "next/link";

import { CoverTable, LegalPage, LegalSection } from "@/components/landing/legal-page-shell";
import { COMMON_PAPER, LEGAL } from "@/lib/legal";
import { contactEmails, siteConfig } from "@/lib/site-config";

const title = "Enterprise order form";
const description = `Order form template for an ${siteConfig.shortName} Enterprise software subscription, on the Common Paper Cloud Service Agreement.`;
const path = "/order-form";

export const metadata: Metadata = { title, description, alternates: { canonical: path } };

export default function OrderFormPage() {
  return (
    <LegalPage title={title} description={description} path={path}>
      <p>
        Enterprise customers sign this order form for a <strong>software subscription</strong>. It is the Cover Page to the{" "}
        <a href={COMMON_PAPER.cloudService.url} target="_blank" rel="noopener noreferrer">
          {COMMON_PAPER.cloudService.name}, version {COMMON_PAPER.cloudService.version}
        </a>
        , licensed under{" "}
        <a href={COMMON_PAPER.license.url} target="_blank" rel="noopener noreferrer">
          {COMMON_PAPER.license.name}
        </a>
        . Self-serve plans need no order form; they are bought on the{" "}
        <Link href="/pricing">pricing page</Link> under the <Link href="/terms">Terms</Link>.
      </p>

      <LegalSection title="Order details">
        <CoverTable
          rows={[
            ["Cloud Service", LEGAL.service],
            ["Plan", "Enterprise"],
            ["Subscription Period", "12 months from the Order Date, unless the signed order states another term"],
            ["Gated AI systems and editors", "As stated in the signed order (Enterprise includes unlimited viewers)"],
            ["Cloud Service Fees", "As stated in the signed order, in USD or EUR, excluding tax"],
            ["Payment Process", "Annual in advance, by invoice or card through Dodo Payments, due within 30 days"],
            ["Non-Renewal Notice Period", "30 days before the end of the Subscription Period"],
            ["Technical Support", `Email support at ${siteConfig.supportEmail} on business days`],
            ["Onboarding", "Onboarding assistance is included in the subscription"],
            ["Professional Services", "None"],
          ]}
        />
      </LegalSection>

      <LegalSection title="Key terms">
        <CoverTable
          rows={[
            ["Provider", LEGAL.provider],
            ["Governing Law", LEGAL.governingLaw],
            ["Chosen Courts", LEGAL.chosenCourts],
            ["General Cap Amount", LEGAL.liabilityCap],
            ["DPA", <Link key="dpa" href="/dpa">Data Processing Agreement</Link>],
            ["Security Policy", <Link key="sec" href="/security">Security</Link>],
            ["Notice Address", LEGAL.noticeEmail],
          ]}
        />
      </LegalSection>

      <LegalSection title="Additional terms">
        <ul>
          <li>
            {siteConfig.shortName} provides evidence and readiness tooling. It is not legal advice, not a notified body,
            and does not certify compliance. Customer remains responsible for its compliance decisions.
          </li>
          <li>The <Link href="/terms#additional-terms">Additional Terms in the Terms of Service</Link> apply.</li>
          <li>Where this order form and the self-serve Terms differ, this order form wins.</li>
        </ul>
      </LegalSection>

      <LegalSection title="How to order">
        <p>
          <Link href="/request-demo">Request a demo</Link> or email {contactEmails.sales} with the number of AI systems
          you want to gate and your billing entity. We send this form with the fees filled in for signature.
        </p>
      </LegalSection>
    </LegalPage>
  );
}
