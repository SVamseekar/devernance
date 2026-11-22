import type { Metadata } from "next";
import Link from "next/link";

import { CookieSettingsButton } from "@/components/cookie-consent";
import { LegalPage, LegalSection } from "@/components/landing/legal-page-shell";
import { contactEmails, siteConfig } from "@/lib/site-config";

const title = "Privacy Policy";
const description = `How ${siteConfig.shortName} collects, uses and protects personal data, and your rights under GDPR, UK GDPR, CCPA/CPRA, and the Australian and New Zealand Privacy Acts.`;
const path = "/privacy";

export const metadata: Metadata = { title, description, alternates: { canonical: path } };

const email = contactEmails.privacy;

export default function PrivacyPage() {
  return (
    <LegalPage title={title} description={description} path={path}>
      <LegalSection title="Who we are">
        <p>
          {siteConfig.ownerName}, trading as {siteConfig.shortName}, is the controller for the personal data described
          here. Contact: <a href={`mailto:${email}`}>{email}</a>.
        </p>
      </LegalSection>

      <LegalSection title="Data we process">
        <ul>
          <li>
            <strong>Account data</strong> (as controller): name, work email, organisation name, role, sign-in events, and
            billing contact details.
          </li>
          <li>
            <strong>Workspace content</strong> (as processor for our customers): AI system records, uploaded evidence,
            evaluation results, approvals and audit events. Our customers decide what goes in; the{" "}
            <Link href="/dpa">DPA</Link> governs it.
          </li>
          <li>
            <strong>Demo requests</strong>: work email, company, role and your message.
          </li>
          <li>
            <strong>Website analytics</strong>: Cloudflare Web Analytics, which is cookieless and builds no personal
            profiles. If you accept analytics cookies, also Google Analytics on our public pages (never sign-in pages
            or your workspace): a pseudonymous cookie identifier, pages viewed without query strings, device and browser data and approximate location. We do not use advertising cookies.
          </li>
        </ul>
      </LegalSection>

      <LegalSection title="Purposes and lawful bases">
        <ul>
          <li>Providing the service and billing: performance of a contract.</li>
          <li>Security, abuse prevention, product improvement and answering demo requests: legitimate interests.</li>
          <li>Tax and accounting records: legal obligation.</li>
          <li>Product update emails: consent, which you can withdraw at any time.</li>
          <li>Google Analytics: consent, which you can withdraw at any time.</li>
        </ul>
      </LegalSection>

      <LegalSection title="Recipients">
        <p>
          We share personal data only with the service providers listed on the{" "}
          <Link href="/subprocessors">subprocessors page</Link>, and where the law requires it. We do not sell personal
          data.
        </p>
      </LegalSection>

      <LegalSection title="International transfers">
        <p>
          The service is hosted in the EU. Administrative access from India, and transfers to subprocessors outside the
          EU, are covered by the EU Standard Contractual Clauses and, for UK data, the UK Addendum.
        </p>
      </LegalSection>

      <LegalSection title="Retention">
        <ul>
          <li>Account data: for the life of the account plus 30 days.</li>
          <li>Backups: 60 days, then overwritten.</li>
          <li>Billing records: as long as Dodo Payments and tax law require.</li>
          <li>Demo requests: 12 months after our last contact.</li>
        </ul>
      </LegalSection>

      <LegalSection title="Cookies">
        <p>
          We set strictly necessary cookies for sign-in and security. Google Analytics cookies (<code>_ga</code>,{" "}
          <code>_ga_*</code>, up to 2 years) are set only after you accept them in the cookie banner. Google receives
          this data in the US under the EU-US Data Privacy Framework, with Google signals and ad personalisation
          turned off. <CookieSettingsButton />.
        </p>
      </LegalSection>

      <LegalSection title="Your rights">
        <ul>
          <li>
            <strong>EU and UK (GDPR, UK GDPR):</strong> access, rectification, erasure, restriction, portability,
            objection, and withdrawal of consent. You can complain to your data protection authority.
          </li>
          <li>
            <strong>California (CCPA/CPRA):</strong> we do not sell or share personal information. You can ask to know,
            delete or correct your personal information, and we will not discriminate against you for doing so.
          </li>
          <li>
            <strong>Australia (Privacy Act 1988, Australian Privacy Principles):</strong> you can access and correct your
            personal information and complain to us; if unresolved, to the Office of the Australian Information
            Commissioner (OAIC).
          </li>
          <li>
            <strong>New Zealand (Privacy Act 2020, Information Privacy Principles):</strong> you can access and correct
            your personal information and complain to the Office of the Privacy Commissioner (OPC).
          </li>
        </ul>
        <p>
          To exercise a right, email <a href={`mailto:${email}`}>{email}</a>. We answer within one month.
        </p>
      </LegalSection>

      <LegalSection title="Automated decisions">
        <p>We do not make decisions that significantly affect individuals by automated means.</p>
      </LegalSection>

      <LegalSection title="Security">
        <p>
          See <Link href="/security">Security</Link> for the controls we run.
        </p>
      </LegalSection>

      <LegalSection title="EU representative">
        <p>To be appointed on our first EU customer contract.</p>
      </LegalSection>

      <LegalSection title="Changes">
        <p>
          We post changes here and update the date above. For material changes we email account owners before they take
          effect.
        </p>
      </LegalSection>

      <LegalSection title="Contact">
        <p>
          <a href={`mailto:${email}`}>{email}</a>
        </p>
      </LegalSection>
    </LegalPage>
  );
}
