"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import type { ApprovalWorkflow, ApprovalStage, StageStatus } from "@/lib/types";
import { ApprovalActionModal } from "./approval-action-modal";
import { CheckCircle2, XCircle, AlertTriangle, Clock, SkipForward } from "lucide-react";
import { STAGE_LABELS, isActionableStage } from "@/lib/workflow-helpers";

interface ApprovalWorkflowPanelProps {
  workflows: ApprovalWorkflow[];
  activeWorkflow: ApprovalWorkflow | null;
  /** The signed-in user's role from /me; undefined while it loads. */
  userRole: string | undefined;
  onApprove: (workflowId: string, stageId: string, rationale: string, oversightEvidence?: string) => void;
  onReject: (workflowId: string, stageId: string, rationale: string) => void;
  onOverride: (workflowId: string, stageId: string, rationale: string) => void;
}

function StageStatusIcon({ status }: { status: StageStatus }) {
  if (status === "APPROVED") return <CheckCircle2 className="w-4 h-4 text-emerald-500" />;
  if (status === "REJECTED") return <XCircle className="w-4 h-4 text-red-500" />;
  if (status === "OVERRIDDEN") return <AlertTriangle className="w-4 h-4 text-amber-500" />;
  if (status === "SKIPPED") return <SkipForward className="w-4 h-4 text-muted-foreground/40" />;
  return <Clock className="w-4 h-4 text-muted-foreground" />;
}

export function ApprovalWorkflowPanel({
  workflows,
  activeWorkflow,
  userRole,
  onApprove,
  onReject,
  onOverride,
}: ApprovalWorkflowPanelProps) {
  const [modal, setModal] = useState<{
    stage: ApprovalStage;
    action: "approve" | "reject" | "override";
    workflowId: string;
  } | null>(null);

  if (!activeWorkflow && workflows.length === 0) {
    return (
      <p className="text-xs text-muted-foreground py-4 text-center">
        No approval workflow for this system.
      </p>
    );
  }

  return (
    <div className="space-y-4">
      {activeWorkflow && (
        <div className="border border-border rounded-xl p-4 space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-foreground uppercase tracking-wider">
              Active Workflow
            </p>
            <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">
              OPEN
            </span>
          </div>
          <div className="space-y-2">
            {activeWorkflow.stages.map((stage) => {
              const actionable = isActionableStage(stage, activeWorkflow.stages, userRole);
              const isAdmin = userRole === "ADMIN";
              return (
                <div
                  key={stage.id}
                  className={cn(
                    "flex items-center justify-between rounded-lg px-3 py-2 border",
                    stage.status === "PENDING" && !actionable
                      ? "border-border bg-muted/20 opacity-50"
                      : "border-border bg-muted/30"
                  )}
                >
                  <div className="flex items-center gap-2">
                    <StageStatusIcon status={stage.status} />
                    <span className="text-xs text-foreground">
                      {STAGE_LABELS[stage.stageType]}
                    </span>
                  </div>
                  {actionable && (
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => setModal({ stage, action: "approve", workflowId: activeWorkflow.id })}
                        className="text-[10px] px-2 py-1 rounded bg-emerald-600 hover:bg-emerald-700 text-white font-medium"
                      >
                        Approve
                      </button>
                      <button
                        onClick={() => setModal({ stage, action: "reject", workflowId: activeWorkflow.id })}
                        className="text-[10px] px-2 py-1 rounded bg-red-600 hover:bg-red-700 text-white font-medium"
                      >
                        Reject
                      </button>
                    </div>
                  )}
                  {!actionable && stage.status === "PENDING" && isAdmin && (
                    <button
                      onClick={() => setModal({ stage, action: "override", workflowId: activeWorkflow.id })}
                      className="text-[10px] px-2 py-1 rounded bg-amber-600 hover:bg-amber-700 text-white font-medium"
                    >
                      Override
                    </button>
                  )}
                  {stage.rationale && (
                    <p className="text-[10px] text-muted-foreground ml-2 truncate max-w-32" title={stage.rationale}>
                      {stage.rationale}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {workflows.filter((w) => w.status !== "OPEN").length > 0 && (
        <div className="space-y-2">
          <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">
            History
          </p>
          {workflows
            .filter((w) => w.status !== "OPEN")
            .map((w) => (
              <div key={w.id} className="border border-border rounded-xl p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] text-muted-foreground">{w.trigger.replace(/_/g, " ")}</span>
                  <span
                    className={cn(
                      "text-[10px] font-medium px-2 py-0.5 rounded-full",
                      w.status === "APPROVED"
                        ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400"
                        : w.status === "REJECTED"
                        ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                        : "bg-muted text-muted-foreground"
                    )}
                  >
                    {w.status}
                  </span>
                </div>
                <div className="space-y-1">
                  {w.stages.map((stage) => (
                    <div key={stage.id} className="flex items-start gap-2">
                      <StageStatusIcon status={stage.status} />
                      <div className="flex-1 min-w-0">
                        <p className="text-[10px] text-foreground">{STAGE_LABELS[stage.stageType]}</p>
                        {stage.rationale && (
                          <p className="text-[10px] text-muted-foreground mt-0.5 leading-relaxed">
                            {stage.rationale}
                          </p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
        </div>
      )}

      {modal && (
        <ApprovalActionModal
          stage={modal.stage}
          action={modal.action}
          onClose={() => setModal(null)}
          onConfirm={(rationale, oversightEvidence) => {
            if (modal.action === "approve") onApprove(modal.workflowId, modal.stage.id, rationale, oversightEvidence);
            else if (modal.action === "reject") onReject(modal.workflowId, modal.stage.id, rationale);
            else onOverride(modal.workflowId, modal.stage.id, rationale);
            setModal(null);
          }}
        />
      )}
    </div>
  );
}
