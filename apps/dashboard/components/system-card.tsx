import Link from "next/link";
import { RiskBadge } from "./risk-badge";
import { DecisionBadge } from "./decision-badge";
import { SectorPackBadge } from "./sector-pack-badge";
import { ReadinessRing } from "./readiness-ring";
import { normaliseDecision } from "@/lib/utils";
import type { AiSystem } from "@/lib/types";
import { useDashboard } from "@/context/dashboard-context";
import { useCertificationReadiness } from "@/hooks/use-certification-readiness";
import { isPublicClaimsSystem } from "@/lib/public-claims";

interface SystemCardProps {
  system: AiSystem;
}

export function SystemCard({ system }: SystemCardProps) {
  const { openSystemDetails } = useDashboard();
  const decision = normaliseDecision(system.releaseDecision);
  const { data: readiness } = useCertificationReadiness(system.id);

  return (
    <div
      onClick={() => openSystemDetails(system.id)}
      className="rounded-xl border border-border p-4 bg-muted/30 hover:bg-muted/40 hover:shadow-xs hover:border-border transition-all cursor-pointer"
    >
      {/* Top row */}
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold leading-snug mb-1.5">{system.name}</h3>
          <div className="flex items-center gap-2 flex-wrap">
            <RiskBadge risk={system.riskClass} />
            <SectorPackBadge sector={system.sector} />
            {isPublicClaimsSystem(system) && (
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                Public claims
              </span>
            )}
            <span className="text-xs text-muted-foreground">{system.owner}</span>
          </div>
        </div>
        <DecisionBadge decision={decision} className="flex-shrink-0" />
      </div>

      {/* Risk basis */}
      <p className="text-xs text-muted-foreground leading-relaxed mb-3 line-clamp-2">
        {system.riskBasis}
      </p>

      {/* Certification readiness ring + top gaps */}
      {readiness && (
        <div
          className="flex items-start gap-3 mb-3 rounded-lg border border-border/70 bg-card/60 p-2.5"
          onClick={(e) => e.stopPropagation()}
        >
          <ReadinessRing
            score={readiness.score}
            status={readiness.readinessStatus}
            size={64}
          />
          <div className="min-w-0 flex-1 pt-0.5">
            <p className="text-[10px] font-semibold text-foreground mb-1">
              Certification readiness
            </p>
            {readiness.gaps.slice(0, 3).length > 0 ? (
              <ul className="space-y-0.5">
                {readiness.gaps.slice(0, 3).map((g) => (
                  <li key={g.code} className="text-[10px] text-muted-foreground line-clamp-1">
                    <span className="font-medium text-foreground/80">{g.severity}</span>
                    {" · "}
                    {g.message}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-[10px] text-muted-foreground">
                No critical gaps — human review still required
              </p>
            )}
            <Link
              href={`/readiness?systemId=${system.id}`}
              className="text-[10px] text-primary font-medium underline mt-1 inline-block"
            >
              View breakdown
            </Link>
          </div>
        </div>
      )}

      {/* Evidence bar */}
      <div className="space-y-1.5">
        <div className="flex justify-between items-center">
          <span className="text-xs text-muted-foreground">Evidence coverage</span>
          <span className="text-xs font-semibold">{system.evidenceCoverage}%</span>
        </div>
        <div className="h-1 rounded-full bg-muted overflow-hidden">
          <div
            className="h-full rounded-full bg-primary transition-all"
            style={{ width: `${system.evidenceCoverage}%` }}
          />
        </div>
      </div>

      {/* Open gaps */}
      {system.openGaps.length > 0 && (
        <div className="mt-3 pt-3 border-t border-border">
          <p className="text-xs text-muted-foreground">
            <span className="text-amber-700 dark:text-amber-400 font-medium">
              {system.openGaps.length} gap{system.openGaps.length !== 1 ? "s" : ""}
            </span>
            {" — "}
            {system.openGaps[0]}
            {system.openGaps.length > 1 && (
              <span className="text-muted-foreground"> +{system.openGaps.length - 1} more</span>
            )}
          </p>
        </div>
      )}
    </div>
  );
}
