/**
 * Regulatory dates shown on the public site. One edit here updates the deadline bar, the home page strip,
 * the EU AI Act page and the deadline calendar tool.
 */
export type Region = "EU" | "US" | "AU" | "NZ";

export interface Deadline {
  id: string;
  region: Region;
  /** Short name of the law, used where the region alone is not enough ("EU AI Act", "Colorado"). */
  law: string;
  label: string;
  /** ISO date the duty applies from. */
  date: string;
  /** Official source; `retrieved` is the ISO date the page was last checked. */
  source: { title: string; url: string; retrieved: string };
}

const RETRIEVED = "2026-10-05";
const AI_ACT = {
  title: "Regulation (EU) 2024/1689 (EUR-Lex)",
  url: "https://eur-lex.europa.eu/eli/reg/2024/1689/oj",
  retrieved: RETRIEVED,
};
const OMNIBUS = {
  title: "Regulation (EU) 2026/1744 (EUR-Lex)",
  url: "https://eur-lex.europa.eu/eli/reg/2026/1744/oj",
  retrieved: RETRIEVED,
};
const AU_PRIVACY = {
  title: "Privacy and Other Legislation Amendment Act 2024 (Federal Register of Legislation)",
  url: "https://www.legislation.gov.au/C2024A00128/latest/text",
  retrieved: RETRIEVED,
};
const CO_SB189 = {
  title: "SB26-189 Automated Decision-Making Technology (Colorado General Assembly)",
  url: "https://leg.colorado.gov/bills/sb26-189",
  retrieved: RETRIEVED,
};

export const DEADLINES: Deadline[] = [
  { id: "eu-art50", region: "EU", law: "EU AI Act", label: "Article 50 transparency duties", date: "2026-08-02", source: AI_ACT },
  {
    id: "eu-art50-2",
    region: "EU",
    law: "EU AI Act",
    label: "Article 50(2) marking: grace period ends for systems on the market before 2 Aug 2026",
    date: "2026-12-02",
    source: OMNIBUS,
  },
  {
    id: "au-adm",
    region: "AU",
    law: "Australian Privacy Act",
    label: "Automated-decision transparency in privacy policies",
    date: "2026-12-10",
    source: AU_PRIVACY,
  },
  {
    id: "us-co-admt",
    region: "US",
    law: "Colorado SB 26-189",
    label: "Automated decision-making technology disclosures",
    date: "2027-01-01",
    source: CO_SB189,
  },
  { id: "eu-annex-iii", region: "EU", law: "EU AI Act", label: "Annex III high-risk duties", date: "2027-12-02", source: OMNIBUS },
  { id: "eu-annex-i", region: "EU", law: "EU AI Act", label: "Annex I embedded high-risk duties", date: "2028-08-02", source: OMNIBUS },
];

/** Whole days from `today` to the deadline (0 or less once it applies). */
export function daysUntil(d: Deadline, today: Date): number {
  return Math.ceil((new Date(`${d.date}T00:00:00Z`).getTime() - today.getTime()) / 86_400_000);
}

export function deadlineStatus(d: Deadline, today: Date): "applies-now" | "upcoming" {
  return new Date(`${d.date}T00:00:00Z`).getTime() <= today.getTime() ? "applies-now" : "upcoming";
}

/** The soonest deadline that has not started yet, or null when every date has passed. */
export function nextDeadline(today: Date, list: Deadline[] = DEADLINES): Deadline | null {
  return (
    list
      .filter((d) => deadlineStatus(d, today) === "upcoming")
      .sort((a, b) => a.date.localeCompare(b.date))[0] ?? null
  );
}

export function formatDeadlineDate(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}
