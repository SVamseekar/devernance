"use client";

import Link from "next/link";
import { ReadinessRing } from "./readiness-ring";
import { useCertificationReadiness } from "@/hooks/use-certification-readiness";
import { Button } from "./ui/button";
import { cn } from "@/lib/utils";
import { ArrowRight, FileDown, AlertTriangle } from "lucide-react";
import { api } from "@/lib/api";
import { useState } from "react";

interface CertificationReadinessCardProps {
  systemId: string;
  systemName?: string;
  compact?: boolean;
}

const DIMENSION_HREF: Record<string, string> = {
  evidence: "/evidence",
  controls: "/systems",
  eval: "/evals",
  contracts: "/contracts",
  approvals: "/approvals",
  determination: "/systems",
  oversight: "/evidence",
  risk: "/systems",
  audit_chain: "/audit",
};

export function CertificationReadinessCard({
  systemId,
  systemName,
  compact = false,
}: CertificationReadinessCardProps) {
  const { data, isFetching, isError } = useCertificationReadiness(systemId);
  const [exporting, setExporting] = useState(false);

  if (!data) {
    return (
      <div className="rounded-xl border border-border p-4 bg-muted/20 text-xs text-muted-foreground">
        Loading certification readiness…
      </div>
    );
  }

  const topGaps = data.gaps.slice(0, 3);

  async function handleExport(format: "json" | "pdf") {
    setExporting(true);
    try {
      await api.systems.certificationReadinessExport(systemId, format);
    } catch {
      // demo/offline — ignore
    } finally {
      setExporting(false);
    }
  }

  return (
    <div
      className={cn(
        "rounded-xl border border-border bg-card p-4",
        compact ? "space-y-3" : "space-y-4"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11px] font-semibold text-foreground">
            Certification readiness
          </p>
          <p className="text-[10px] text-muted-foreground leading-relaxed mt-0.5">
            Score + gaps toward conformity documentation —{" "}
            <strong className="font-semibold text-foreground">not legal certification</strong>.
          </p>
          {systemName && (
            <p className="text-[10px] text-muted-foreground mt-1 truncate">{systemName}</p>
          )}
        </div>
        <ReadinessRing score={data.score} status={data.readinessStatus} size={compact ? 72 : 88} />
      </div>

      {topGaps.length > 0 ? (
        <div className="space-y-1.5">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
            Top gaps
          </p>
          {topGaps.map((gap) => (
            <div
              key={gap.code}
              className="flex items-start gap-2 rounded-lg border border-border/80 bg-muted/20 px-2.5 py-2"
            >
              <AlertTriangle
                className={cn(
                  "w-3 h-3 mt-0.5 shrink-0",
                  gap.severity === "CRITICAL" && "text-red-500",
                  gap.severity === "HIGH" && "text-amber-500",
                  (gap.severity === "MEDIUM" || gap.severity === "LOW") && "text-muted-foreground"
                )}
              />
              <div className="min-w-0 flex-1">
                <p className="text-[11px] font-medium leading-snug">{gap.message}</p>
                <p className="text-[10px] text-muted-foreground mt-0.5 line-clamp-2">
                  {gap.remediationHint}
                </p>
                <Link
                  href={DIMENSION_HREF[gap.dimension] ?? "/systems"}
                  className="inline-flex items-center gap-0.5 text-[10px] text-primary font-medium mt-1 underline"
                >
                  Open {gap.dimension.replace("_", " ")}
                  <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-[11px] text-muted-foreground">
          No structured gaps — still requires human review before any external conformity process.
        </p>
      )}

      {!compact && (
        <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-border">
          <Link
            href={`/readiness?systemId=${systemId}`}
            className="inline-flex h-7 items-center rounded-lg border border-border bg-background px-2.5 text-[10px] font-medium hover:bg-muted"
          >
            Full breakdown
            <ArrowRight className="w-3 h-3 ml-1" />
          </Link>
          <Button
            size="sm"
            variant="ghost"
            className="h-7 text-[10px]"
            disabled={exporting || isError}
            onClick={() => handleExport("json")}
          >
            <FileDown className="w-3 h-3 mr-1" />
            Export JSON
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="h-7 text-[10px]"
            disabled={exporting || isError}
            onClick={() => handleExport("pdf")}
          >
            <FileDown className="w-3 h-3 mr-1" />
            Export PDF
          </Button>
          {isFetching && (
            <span className="text-[10px] text-muted-foreground ml-auto">Refreshing…</span>
          )}
        </div>
      )}
    </div>
  );
}
