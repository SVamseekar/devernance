import type { ControlStatus } from "./types";

/**
 * Mirrors the API's separation of duties so the dashboard only offers what the server will accept: the engineering
 * lead may raise a control to REVIEW or BLOCKED, but only compliance or an admin marks PASS or lifts a block.
 */
export function allowedControlStatuses(role: string | undefined, current: ControlStatus): ControlStatus[] {
  if (role === "ADMIN" || role === "COMPLIANCE_OFFICER") return ["PASS", "REVIEW", "BLOCKED"];
  if (role === "AI_ENGINEERING_LEAD") return current === "BLOCKED" ? ["BLOCKED"] : ["REVIEW", "BLOCKED"];
  return [];
}

/** Annex IV dossier and FRIA record: compliance, legal and admins (ConformityController). */
export function canEditConformity(role: string | undefined): boolean {
  return role === "ADMIN" || role === "COMPLIANCE_OFFICER" || role === "LEGAL_COUNSEL";
}
