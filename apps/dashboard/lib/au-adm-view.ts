import type { AuAdmSystem } from "./types";

export const AU_ADM_UPGRADE_TEXT = "Available on Business and above";

const FIELD_LABELS: Record<string, string> = {
  purpose: "purpose",
  decisionImpact: "decision impact",
  affectedUsers: "affected people",
  dataSources: "data sources",
};

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

export interface AdmRow {
  systemId: string;
  name: string;
  status: AuAdmSystem["status"];
  detail: string;
  /** Only included systems have a draft to copy; nothing is drafted from missing facts. */
  copyText: string | null;
}

const ORDER: Record<AuAdmSystem["status"], number> = { needs_input: 0, include: 1, not_applicable: 2 };

export function admRows(systems: AuAdmSystem[]): AdmRow[] {
  return [...systems]
    .sort((a, b) => ORDER[a.status] - ORDER[b.status] || a.name.localeCompare(b.name))
    .map((s) => ({
      systemId: s.systemId,
      name: s.name,
      status: s.status,
      detail: s.status === "needs_input" ? `Missing: ${s.missing.map(fieldLabel).join(", ")}` : s.reason,
      copyText: s.status === "include" ? s.draftStatement : null,
    }));
}

export function admSummary(systems: AuAdmSystem[]) {
  return {
    include: systems.filter((s) => s.status === "include").length,
    needsInput: systems.filter((s) => s.status === "needs_input").length,
    notApplicable: systems.filter((s) => s.status === "not_applicable").length,
  };
}
