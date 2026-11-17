import type {
  GovernanceDecision,
  GovernanceDecisionStatus,
  GovernanceSystemStatus,
} from "./types";

/** Badge colours per decision; ALLOW green, REVIEW amber, DENY red. */
export const DECISION_TONE: Record<GovernanceDecisionStatus, string> = {
  ALLOW: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-400",
  REVIEW: "bg-amber-50 text-amber-800 dark:bg-amber-950/50 dark:text-amber-400",
  DENY: "bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-400",
};

/** One line for a decision's outcome, e.g. "approved, executed" or "denied". */
export function decisionOutcome(decision: GovernanceDecision): string {
  const parts: string[] = [];
  if (!decision.enforced) parts.push("monitor only");
  if (decision.approval) parts.push(decision.approval.status.toLowerCase());
  if (decision.execution) parts.push(decision.execution.status.toLowerCase());
  else if (decision.authorized && decision.status !== "DENY") parts.push("outcome not reported");
  if (decision.status === "DENY" && decision.enforced) parts.push("blocked");
  return parts.join(", ");
}

/** "amount: 75000 · currency: INR": the request facts an approver needs, flat and short. */
export function describeFacts(...maps: Record<string, unknown>[]): string {
  const entries: string[] = [];
  for (const map of maps) {
    for (const [key, value] of Object.entries(map ?? {})) {
      const text = typeof value === "object" && value !== null ? JSON.stringify(value) : String(value);
      entries.push(`${key}: ${text.length > 60 ? `${text.slice(0, 57)}...` : text}`);
    }
  }
  return entries.join(" · ");
}

/** Systems that need someone's attention first: validation errors, then drift, then pending approvals. */
export function attentionOrder(systems: GovernanceSystemStatus[]): GovernanceSystemStatus[] {
  const score = (s: GovernanceSystemStatus) =>
    (s.validation.valid ? 0 : 4) + (s.drift.some((d) => !d.inSync) ? 2 : 0) + (s.pendingApprovals > 0 ? 1 : 0);
  return [...systems].sort((a, b) => score(b) - score(a) || a.system.localeCompare(b.system));
}

/** Whether the signed-in role may answer an approval that names these roles. */
export function canAnswer(role: string | undefined, approverRoles: string[]): boolean {
  return !!role && approverRoles.includes(role);
}
