import type { Metadata } from "next";
import Link from "next/link";

import { CoverTable, LegalPage, LegalSection } from "@/components/landing/legal-page-shell";
import { COMMON_PAPER, LEGAL } from "@/lib/legal";
import { contactEmails, siteConfig } from "@/lib/site-config";

const title = "Terms of Service";
const description = `The terms for using ${siteConfig.shortName}: a Cover Page on the Common Paper Cloud Service Agreement Standard Terms.`;
const path = "/terms";

export const metadata: Metadata = { title, description, alternates: { canonical: path } };

export default function TermsPage() {
  return (
    <LegalPage title={title} description={description} path={path}>
      <LegalSection title="Agreement">
        <p>
          These Terms are our Cover Page. They incorporate the{" "}
          <a href={COMMON_PAPER.cloudService.url} target="_blank" rel="noopener noreferrer">
            {COMMON_PAPER.cloudService.name}, version {COMMON_PAPER.cloudService.version}
          </a>
          , licensed under{" "}
          <a href={COMMON_PAPER.license.url} target="_blank" rel="noopener noreferrer">
            {COMMON_PAPER.license.name}
          </a>
          . Capitalised words not defined here have the meaning given in the Standard Terms. By creating a workspace or
          using the service, you accept these Terms on behalf of your organisation (the Customer).
        </p>
      </LegalSection>

      <LegalSection title="Cover Page">
        <CoverTable
          rows={[
            ["Provider", LEGAL.provider],
            ["Cloud Service", LEGAL.service],
            ["Subscription Period", "Monthly or yearly, as chosen at checkout; Free plans run until cancelled"],
            ["Cloud Service Fees", <Link key="p" href="/pricing">As listed on the pricing page at purchase</Link>],
            ["Payment Process", LEGAL.merchantOfRecord],
            ["Non-Renewal Notice Period", "Cancel any time before the renewal date in the customer portal"],
            ["Technical Support", `Email ${siteConfig.supportEmail}`],
            ["Governing Law", LEGAL.governingLaw],
            ["Chosen Courts", LEGAL.chosenCourts],
            ["General Cap Amount", LEGAL.liabilityCap],
            ["DPA", <Link key="d" href="/dpa">Data Processing Agreement</Link>],
            ["Security Policy", <Link key="s" href="/security">Security</Link>],
            ["Notice Address", LEGAL.noticeEmail],
          ]}
        />
      </LegalSection>

      <LegalSection title="Additional Terms" id="additional-terms">
        <ul>
          <li>
            <strong>Not legal advice.</strong> {siteConfig.shortName} provides evidence and readiness tooling. It is not
            legal advice, not a notified body, and does not certify compliance. Outputs (risk suggestions, obligation
            maps, release decisions, evidence packs) are evidence and readiness aids. Customer remains responsible for its
            compliance decisions.
          </li>
          <li>
            <strong>Demo workspace.</strong> The live demo is a shared, read-only workspace with sample data. Do not enter
            personal or confidential data in it.
          </li>
          <li>
            <strong>Acceptable use.</strong> No unlawful content, no probing or attempting to bypass tenant isolation,
            no security testing without written permission, and no reverse engineering beyond what the law allows.
          </li>
          <li>
            <strong>Data export.</strong> After termination you can export your workspace data for {LEGAL.exportDays}{" "}
            days; then we delete it, except for backups that expire on their normal schedule.
          </li>
          <li>
            <strong>Price changes.</strong> We give {LEGAL.priceChangeNoticeDays} days&apos; notice of price changes,
            effective at your next renewal.
          </li>
          <li>
            <strong>Refunds.</strong> See the <Link href="/refunds">refund policy</Link>.
          </li>
          <li>
            <strong>Payments.</strong> {LEGAL.merchantOfRecord}
          </li>
          <li>
            <strong>Enterprise.</strong> A signed <Link href="/order-form">order form</Link> overrides these Terms where
            they differ.
          </li>
        </ul>
      </LegalSection>

      <LegalSection title="Contact">
        <p>
          Questions about these Terms: <a href={`mailto:${contactEmails.legal}`}>{contactEmails.legal}</a>.
        </p>
      </LegalSection>
    </LegalPage>
  );
}
