import { siteConfig } from "@/lib/site-config";

/**
 * Shared legal values used by the Terms, DPA, MSA and order form pages, so a change (a company is formed,
 * counsel picks another governing law, Common Paper publishes a new version) is one edit.
 * Common Paper standard agreements are licensed under CC BY 4.0 and incorporated by reference.
 */
export const COMMON_PAPER = {
  cloudService: {
    name: "Common Paper Cloud Service Agreement Standard Terms",
    version: "2.1",
    url: "https://commonpaper.com/standards/cloud-service-agreement/2.1/",
  },
  dpa: {
    name: "Common Paper Data Processing Agreement Standard Terms",
    version: "1.1",
    url: "https://commonpaper.com/standards/data-processing-agreement/1.1/",
  },
  license: { name: "CC BY 4.0", url: "https://creativecommons.org/licenses/by/4.0/" },
} as const;

export const LEGAL = {
  provider: `${siteConfig.ownerName}, trading as ${siteConfig.shortName} (formerly ${siteConfig.formerName})`,
  service: `${siteConfig.shortName} hosted software (release gates, evidence records, evidence packs)`,
  /** Plan 08 default for an EU-facing product; the owner confirms with counsel. */
  governingLaw: "The laws of Ireland",
  chosenCourts: "The courts of Dublin, Ireland",
  liabilityCap:
    "The fees paid or payable by Customer for the Cloud Service in the 12 months before the event giving rise to the claim. Neither party is liable for indirect or consequential damages.",
  merchantOfRecord: "Dodo Payments is the merchant of record for purchases; its terms apply to the payment transaction.",
  noticeEmail: siteConfig.supportEmail,
  exportDays: 30,
  priceChangeNoticeDays: 30,
  refundDays: 14,
} as const;

/** Version of our DPA Cover Page; stored with each workspace's acceptance (max 16 characters). */
export const DPA_VERSION = "cp1.1-2026-10";
