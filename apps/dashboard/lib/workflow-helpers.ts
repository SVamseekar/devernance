import { isLiveEntityId } from "./ids";
import type { ApprovalStage, ApprovalWorkflow } from "./types";

export const STAGE_LABELS: Record<string, string> = {
  ENG_LEAD_REVIEW: "Engineering Lead Review",
  COMPLIANCE_REVIEW: "Compliance Review",
  LEGAL_SIGNOFF: "Legal Sign-off",
};

export function firstPendingStage(workflow: ApprovalWorkflow) {
  const done = new Set(["APPROVED", "OVERRIDDEN"]);
  const stages = [...(workflow.stages ?? [])].sort(
    (a, b) => (a.stageOrder ?? 0) - (b.stageOrder ?? 0),
  );
  return stages.find((s) => !done.has(String(s.status).toUpperCase())) ?? null;
}

/**
 * Whether the signed-in user (by their real role from /me) can act on this stage now. Mirrors the API's rule:
 * the next pending stage, for its required role or for an admin. The API still decides; this only hides buttons.
 */
export function isActionableStage(
  stage: ApprovalStage,
  stages: ApprovalStage[],
  userRole: string | undefined,
): boolean {
  if (stage.status !== "PENDING" || !userRole) return false;
  const priorPending = stages.some(
    (s) => s.stageOrder < stage.stageOrder && s.status === "PENDING"
  );
  if (priorPending) return false;
  return userRole === stage.requiredRole || userRole === "ADMIN";
}

/**
 * The approval workflows to show for one system: the API's open workflows for a real system, and the sample
 * workflows only when mock fallback is on. A real system never borrows sample approvals.
 */
export function workflowsForSystem(
  systemId: string,
  openWorkflows: ApprovalWorkflow[],
  mockWorkflows: Record<string, ApprovalWorkflow[]>,
  allowMock: boolean,
): ApprovalWorkflow[] {
  if (isLiveEntityId(systemId)) return openWorkflows.filter((w) => w.systemId === systemId);
  return allowMock ? mockWorkflows[systemId] ?? [] : [];
}
