import type { Metadata } from "next";

import { LegalPage, LegalSection } from "@/components/landing/legal-page-shell";
import { LEGAL } from "@/lib/legal";
import { contactEmails, siteConfig } from "@/lib/site-config";

const title = "Refund policy";
const description = `Refunds within ${LEGAL.refundDays} days of your first payment on any ${siteConfig.shortName} plan.`;
const path = "/refunds";

export const metadata: Metadata = { title, description, alternates: { canonical: path } };

export default function RefundsPage() {
  return (
    <LegalPage title={title} description={description} path={path}>
      <LegalSection title="First payment">
        <p>
          Refunds within {LEGAL.refundDays} days of your first payment for any plan, no questions asked. Email{" "}
          <a href={`mailto:${contactEmails.refunds}`}>{contactEmails.refunds}</a> or use the customer portal.
        </p>
      </LegalSection>
      <LegalSection title="Renewals">
        <p>Renewals are not refunded. Cancel any time to stop the next renewal; access continues to the end of the paid period.</p>
      </LegalSection>
      <LegalSection title="Consumer law">
        <p>Where consumer law gives you more rights, those rights apply.</p>
      </LegalSection>
      <LegalSection title="Who processes refunds">
        <p>{LEGAL.merchantOfRecord} Refunds go back to the original payment method.</p>
      </LegalSection>
    </LegalPage>
  );
}
