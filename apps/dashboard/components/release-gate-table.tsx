import { RiskBadge } from "./risk-badge";
import { DecisionBadge } from "./decision-badge";
import { normaliseDecision, cn } from "@/lib/utils";
import type { AiSystem } from "@/lib/types";
import { MOCK_WORKFLOWS } from "@/lib/mock-data";
import { allowMockFallback } from "@/lib/live-mode";
import { workflowsForSystem } from "@/lib/workflow-helpers";
import { useOpenWorkflows } from "@/hooks/use-open-workflows";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { isLiveEntityId } from "@/lib/ids";

interface ReleaseGateTableProps {
  systems: AiSystem[];
}

export function ReleaseGateTable({ systems }: ReleaseGateTableProps) {
  const { data: openWorkflows = [] } = useOpenWorkflows();
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">System</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">Risk</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">Evidence</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">Eval</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">Contract</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">Decision</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground pr-4">Control mode</th>
            <th className="text-left pb-3 text-xs font-medium text-muted-foreground">Workflow</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {systems.map((system) => {
            const decision = normaliseDecision(system.releaseDecision);
            return (
              <tr key={system.id} className="hover:bg-muted/30 transition-colors">
                <td className="py-3.5 pr-4">
                  <p className="font-medium text-sm">{system.name}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{system.owner}</p>
                </td>
                <td className="py-3.5 pr-4">
                  <RiskBadge risk={system.riskClass} />
                </td>
                <td className="py-3.5 pr-4">
                  <div className="flex items-center gap-2.5">
                    <div className="w-20 h-1 rounded-full bg-muted overflow-hidden">
                      <div
                        className="h-full rounded-full bg-primary"
                        style={{ width: `${system.evidenceCoverage}%` }}
                      />
                    </div>
                    <span className="text-xs text-muted-foreground">{system.evidenceCoverage}%</span>
                  </div>
                </td>
                <td className="py-3.5 pr-4">
                  <span
                    className={cn(
                      "text-sm font-medium",
                      system.evalScore >= 85
                        ? "text-emerald-700 dark:text-emerald-400"
                        : "text-amber-700 dark:text-amber-400"
                    )}
                  >
                    {system.evalScore}%
                  </span>
                </td>
                <td className="py-3.5 pr-4">
                  <span
                    className={cn(
                      "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium",
                      system.dataContractStatus === "BREACH" &&
                        "bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-400",
                      system.dataContractStatus === "WARNING" &&
                        "bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-400",
                      system.dataContractStatus === "HEALTHY" &&
                        "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-400"
                    )}
                  >
                    {system.dataContractStatus}
                  </span>
                </td>
                <td className="py-3.5 pr-4">
                  <DecisionBadge decision={decision} />
                </td>
                <td className="py-3.5 pr-4">
                  <ControlModeCell systemId={system.id} />
                </td>
                <td className="py-3.5">
                  {(() => {
                    const wfs = workflowsForSystem(system.id, openWorkflows, MOCK_WORKFLOWS, allowMockFallback());
                    const open = wfs.find((w) => w.status === "OPEN");
                    const last = wfs[0];
                    if (open) {
                      const activeStage = open.stages.find((s) => s.status === "PENDING");
                      const stagePos = activeStage ? `Stage ${activeStage.stageOrder}/${open.stages.filter(s => s.status !== "SKIPPED").length}` : "";
                      return (
                        <span className="inline-flex items-center gap-1 text-[10px] font-medium text-amber-700 dark:text-amber-400">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 inline-block" />
                          OPEN · {stagePos}
                        </span>
                      );
                    }
                    if (last?.status === "APPROVED") {
                      return <span className="text-[10px] text-emerald-700 dark:text-emerald-400 font-medium">✓ Approved</span>;
                    }
                    if (last?.status === "REJECTED") {
                      return <span className="text-[10px] text-red-700 dark:text-red-400 font-medium">✗ Rejected</span>;
                    }
                    return <span className="text-[10px] text-muted-foreground">—</span>;
                  })()}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ControlModeCell({ systemId }: { systemId: string }) {
  const live = isLiveEntityId(systemId);
  const gate = useQuery({
    queryKey: ["release-gate", systemId],
    queryFn: () => api.systems.releaseGate(systemId),
    enabled: live,
  });
  const controls = gate.data?.controls ?? [];
  if (!live) {
    return <span className="text-[10px] text-muted-foreground">—</span>;
  }
  if (gate.isLoading) {
    return <span className="text-[10px] text-muted-foreground">…</span>;
  }
  if (controls.length === 0) {
    return <span className="text-[10px] text-muted-foreground">No accepted link</span>;
  }
  return (
    <div className="flex flex-col gap-1">
      {controls.map((control) => (
        <span
          key={control.proposalId}
          className="inline-flex w-fit items-center rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium"
        >
          {control.mode}
          {control.forceFrom ? ` · ${control.forceFrom}` : ""}
        </span>
      ))}
    </div>
  );
}
