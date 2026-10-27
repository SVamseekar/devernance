"use client";

import type { AiSystem } from "@/lib/types";
import { normaliseDecision, cn } from "@/lib/utils";
import { RiskBadge } from "@/components/risk-badge";
import { DecisionBadge } from "@/components/decision-badge";
import { useDashboard } from "@/context/dashboard-context";

interface RiskTopologyProps {
  systems: AiSystem[];
  filter: "all" | "high" | "limited" | "minimal";
}

export function RiskTopology({ systems, filter }: RiskTopologyProps) {
  const { openSystemDetails } = useDashboard();
  const filtered = systems.filter((s) => filter === "all" || s.riskClass === filter);

  return (
    <div>
      {/* Column headers */}
      <div className="grid grid-cols-[1fr_180px_72px_80px_90px] gap-4 px-3 pb-2.5 border-b border-border mb-1">
        <span className="text-xs font-medium text-muted-foreground">System</span>
        <span className="text-xs font-medium text-muted-foreground">Evidence coverage</span>
        <span className="text-xs font-medium text-muted-foreground text-right">Eval</span>
        <span className="text-xs font-medium text-muted-foreground">Risk</span>
        <span className="text-xs font-medium text-muted-foreground">Decision</span>
      </div>

      {filtered.map((system) => {
        const decision = normaliseDecision(system.releaseDecision);
        const barColor =
          system.riskClass === "high"
            ? "bg-red-500"
            : system.riskClass === "limited"
            ? "bg-amber-500"
            : "bg-emerald-500";

        return (
          <div
            key={system.id}
            onClick={() => openSystemDetails(system.id)}
            className="grid grid-cols-[1fr_180px_72px_80px_90px] gap-4 items-center px-3 py-3 rounded-lg hover:bg-muted/40 hover:shadow-xs border border-transparent hover:border-border transition-all cursor-pointer"
          >
            {/* System name */}
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{system.name}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{system.owner}</p>
            </div>

            {/* Evidence bar + % */}
            <div className="flex items-center gap-2.5">
              <div className="flex-1 h-2 rounded-full bg-muted overflow-hidden">
                <div
                  className={cn("h-full rounded-full", barColor)}
                  style={{ width: `${system.evidenceCoverage}%` }}
                />
              </div>
              <span className="text-xs font-medium text-muted-foreground w-8 text-right flex-shrink-0">
                {system.evidenceCoverage}%
              </span>
            </div>

            {/* Eval score */}
            <div className="text-right">
              <span
                className={cn(
                  "text-sm font-semibold",
                  system.evalScore >= 85
                    ? "text-emerald-700 dark:text-emerald-400"
                    : system.evalScore >= 70
                    ? "text-amber-700 dark:text-amber-400"
                    : "text-red-700 dark:text-red-400"
                )}
              >
                {system.evalScore}%
              </span>
            </div>

            {/* Risk badge */}
            <div>
              <RiskBadge risk={system.riskClass} />
            </div>

            {/* Decision */}
            <div>
              <DecisionBadge decision={decision} />
            </div>
          </div>
        );
      })}

      {filtered.length === 0 && (
        <div className="py-10 text-center text-sm text-muted-foreground">
          No systems match this filter.
        </div>
      )}
    </div>
  );
}
