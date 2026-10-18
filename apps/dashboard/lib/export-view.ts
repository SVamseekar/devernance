export type SystemExportKind = "questionnaire" | "grc";

export interface SystemExport {
  kind: SystemExportKind;
  label: string;
  description: string;
  button: string;
  upgradeText: string;
  fallbackFilename: (systemId: string) => string;
}

/** Per-system downloads built from recorded evidence. Both are drafts or inputs for the customer's own review. */
export const SYSTEM_EXPORTS: SystemExport[] = [
  {
    kind: "questionnaire",
    label: "Questionnaire answers",
    description:
      "Draft answers to 25 common AI vendor due-diligence questions, filled only from this system's records. " +
      "Anything not recorded is marked for you to answer manually.",
    button: "Download CSV",
    upgradeText: "Available on Business and above",
    fallbackFilename: (systemId) => `questionnaire-ai-vendor-core-${systemId}.csv`,
  },
  {
    kind: "grc",
    label: "GRC export",
    description:
      "A ZIP with an index of this system's indexed documents, the signed evidence pack, and the guide for " +
      "verifying it. Upload it as custom evidence in a GRC tool such as Vanta or Drata.",
    button: "Download ZIP",
    upgradeText: "Available on Business and above",
    fallbackFilename: (systemId) => `grc-export-${systemId}.zip`,
  },
];

export function exportPath(systemId: string, kind: SystemExportKind): string {
  return kind === "questionnaire"
    ? `/systems/${systemId}/questionnaire-export?format=csv`
    : `/systems/${systemId}/grc-export`;
}

/** Filename from a Content-Disposition header, without any path, or the fallback when there is none. */
export function downloadFilename(disposition: string | null, fallback: string): string {
  const match = /filename="?([^";]+)"?/i.exec(disposition ?? "");
  const name = match?.[1].split(/[\\/]/).pop()?.trim();
  return name ? name : fallback;
}
