import type { Metadata } from "next";

import { LegalPage, LegalSection } from "@/components/landing/legal-page-shell";
import { contactEmails, siteConfig } from "@/lib/site-config";
import { SUBPROCESSORS } from "@/lib/subprocessors";

const title = "Subprocessors";
const description = `Third parties that process personal data for ${siteConfig.shortName}, what they do, and where.`;
const path = "/subprocessors";

export const metadata: Metadata = { title, description, alternates: { canonical: path } };

export default function SubprocessorsPage() {
  return (
    <LegalPage title={title} description={description} path={path}>
      <p>
        We give 30 days&apos; notice before adding a subprocessor. To be notified, email{" "}
        <a href={`mailto:${contactEmails.privacy}`}>{contactEmails.privacy}</a>.
      </p>
      <LegalSection title="Current subprocessors">
        <div className="overflow-x-auto rounded-xl border border-line bg-white">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="bg-mist text-ink-muted">
              <tr>
                <th className="px-4 py-2.5 font-semibold">Name</th>
                <th className="px-4 py-2.5 font-semibold">Purpose</th>
                <th className="px-4 py-2.5 font-semibold">Location</th>
                <th className="px-4 py-2.5 font-semibold">Data</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {SUBPROCESSORS.map((s) => (
                <tr key={s.name} className="align-top">
                  <td className="px-4 py-3 font-semibold text-ink">
                    <a href={s.link} target="_blank" rel="noopener noreferrer">
                      {s.name}
                    </a>
                  </td>
                  <td className="px-4 py-3">{s.purpose}</td>
                  <td className="px-4 py-3">{s.location}</td>
                  <td className="px-4 py-3">{s.dataCategories}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </LegalSection>
    </LegalPage>
  );
}
