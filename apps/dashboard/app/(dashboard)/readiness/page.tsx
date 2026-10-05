"use client";

import { Suspense, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { ReadinessRing } from "@/components/readiness-ring";
import { AuAdmCard } from "@/components/au-adm-card";
import { useDashboard } from "@/context/dashboard-context";
import { useCertificationReadiness } from "@/hooks/use-certification-readiness";
import { useAssessment } from "@/hooks/use-assessment";
import { api } from "@/lib/api";
import { isLiveEntityId } from "@/lib/ids";
import { cn } from "@/lib/utils";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  FileDown,
  ShieldCheck,
} from "lucide-react";

const CTA_HREF: Record<string, string> = {
  evidence: "/evidence",
  controls: "/systems",
  eval: "/evals",
  contracts: "/contracts",
  approvals: "/approvals",
  determination: "/systems",
  oversight: "/evidence",
  risk: "/systems",
  audit_chain: "/audit",
  release_gate: "/systems",
};

export default function ReadinessPage() {
  return (
    <Suspense
      fallback={
        <div className="text-sm text-muted-foreground p-4">Loading readiness…</div>
      }
    >
      <ReadinessPageInner />
    </Suspense>
  );
}

function Count({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-border px-3 py-2">
      <p className="text-lg font-semibold tabular-nums">{value}</p>
      <p className="text-[10px] text-muted-foreground">{label}</p>
    </div>
  );
}

function ReadinessPageInner() {
  const { allSystems } = useDashboard();
  const searchParams = useSearchParams();
  const liveSystems = useMemo(
    () => allSystems.filter((system) => isLiveEntityId(system.id)),
    [allSystems]
  );
  const [pickedId, setPickedId] = useState(searchParams.get("systemId") ?? "");
  const systemId = liveSystems.some((system) => system.id === pickedId)
    ? pickedId
    : (liveSystems[0]?.id ?? "");
  const { data, isError, isFetching } = useCertificationReadiness(systemId || null);
  const assessment = useAssessment(systemId || null);
  const [exporting, setExporting] = useState(false);

  const selectedName = useMemo(
    () => allSystems.find((s) => s.id === systemId)?.name ?? data?.systemName,
    [allSystems, systemId, data?.systemName]
  );

  async function handleExport(format: "json" | "pdf") {
    if (!systemId) return;
    setExporting(true);
    try {
      await api.systems.certificationReadinessExport(systemId, format);
    } catch {
      // offline / mock
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-amber-200/80 dark:border-amber-900/40 bg-amber-50/40 dark:bg-amber-950/15 px-4 py-3">
        <p className="text-[11px] font-semibold text-foreground mb-0.5 flex items-center gap-1.5">
          <ShieldCheck className="w-3.5 h-3.5" />
          Evidence count
        </p>
        <p className="text-[10px] text-muted-foreground leading-relaxed">
          Count of evidence for this system and this corpus version.
          A person still reviews the file before any external process.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1.5 min-w-[220px]">
          <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
            AI system
          </label>
          <Select
            value={systemId}
            onValueChange={(value) => {
              if (isLiveEntityId(value)) setPickedId(value);
            }}
          >
            <SelectTrigger className="h-9 text-xs">
              <SelectValue placeholder="Select system">
                {selectedName ?? "Select system"}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {liveSystems.map((s) => (
                <SelectItem key={s.id} value={s.id} className="text-xs">
                  {s.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            className="h-9 text-[11px]"
            disabled={!systemId || exporting || isError}
            onClick={() => handleExport("json")}
          >
            <FileDown className="w-3.5 h-3.5 mr-1.5" />
            Export JSON
          </Button>
          <Button
            size="sm"
            variant="outline"
            className="h-9 text-[11px]"
            disabled={!systemId || exporting || isError}
            onClick={() => handleExport("pdf")}
          >
            <FileDown className="w-3.5 h-3.5 mr-1.5" />
            Export PDF
          </Button>
        </div>
        {isFetching && (
          <span className="text-[10px] text-muted-foreground pb-2">Refreshing…</span>
        )}
      </div>

      {assessment.data && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Evidence count</CardTitle>
            <CardDescription className="text-[10px] font-mono break-all">
              Corpus version {assessment.data.corpusVersion}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid grid-cols-2 sm:grid-cols-3 gap-3 pb-4">
            <Count label="Satisfied" value={assessment.data.counts.satisfied} />
            <Count label="Insufficient" value={assessment.data.counts.insufficient} />
            <Count label="Missing" value={assessment.data.counts.missing} />
            <Count label="Needs human review" value={assessment.data.counts.needsHumanReview} />
            <Count label="Not applicable" value={assessment.data.counts.notApplicable} />
            <Count label="Accepted exception" value={assessment.data.counts.acceptedException} />
          </CardContent>
        </Card>
      )}

      {data && (
        <>
          <div className="grid grid-cols-3 gap-4">
            <Card className="col-span-1">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">Readiness score</CardTitle>
                <CardDescription className="text-[10px]">
                  {selectedName ?? data.systemName}
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col items-center pb-6">
                <ReadinessRing score={data.score} status={data.readinessStatus} size={120} />
                <p className="text-[10px] text-muted-foreground text-center mt-3 max-w-[200px] leading-relaxed">
                  Score is a file review aid only.
                </p>
              </CardContent>
            </Card>

            <Card className="col-span-2">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">Dimension breakdown</CardTitle>
                <CardDescription className="text-[10px]">
                  Weighted dimensions sum to 100.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-2 pb-4">
                {data.dimensions.map((dim) => (
                  <div key={dim.code} className="space-y-1">
                    <div className="flex items-center justify-between gap-2 text-[11px]">
                      <span className="font-medium truncate">
                        {dim.label}
                        <span className="text-muted-foreground font-normal ml-1">
                          w{dim.weight}
                        </span>
                      </span>
                      <span
                        className={cn(
                          "tabular-nums font-semibold shrink-0",
                          dim.status === "PASS" && "text-emerald-600 dark:text-emerald-400",
                          dim.status === "PARTIAL" && "text-amber-600 dark:text-amber-400",
                          dim.status === "FAIL" && "text-red-600 dark:text-red-400"
                        )}
                      >
                        {dim.score}%
                      </span>
                    </div>
                    <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                      <div
                        className={cn(
                          "h-full rounded-full transition-all",
                          dim.status === "PASS" && "bg-emerald-500",
                          dim.status === "PARTIAL" && "bg-amber-500",
                          dim.status === "FAIL" && "bg-red-500"
                        )}
                        style={{ width: `${dim.score}%` }}
                      />
                    </div>
                    <p className="text-[10px] text-muted-foreground">{dim.summary}</p>
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Structured gaps</CardTitle>
              <CardDescription className="text-[10px]">
                Code · severity · message · remediation. CTAs open the relevant workspace.
              </CardDescription>
            </CardHeader>
            <CardContent className="px-0 pb-0">
              {data.gaps.length === 0 ? (
                <p className="px-6 pb-6 text-xs text-muted-foreground">
                  No structured gaps recorded. A person still reviews the file.
                </p>
              ) : (
                <div className="divide-y divide-border">
                  {data.gaps.map((gap) => (
                    <div
                      key={`${gap.code}-${gap.message}`}
                      className="flex items-start justify-between gap-4 px-6 py-3"
                    >
                      <div className="flex items-start gap-2.5 min-w-0">
                        <AlertTriangle
                          className={cn(
                            "w-3.5 h-3.5 mt-0.5 shrink-0",
                            gap.severity === "CRITICAL" && "text-red-500",
                            gap.severity === "HIGH" && "text-amber-500",
                            (gap.severity === "MEDIUM" || gap.severity === "LOW") &&
                              "text-muted-foreground"
                          )}
                        />
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-1.5 mb-0.5">
                            <span className="text-[10px] font-mono font-semibold text-muted-foreground">
                              {gap.code}
                            </span>
                            <span
                              className={cn(
                                "text-[9px] font-bold uppercase tracking-wide rounded px-1.5 py-0.5",
                                gap.severity === "CRITICAL" &&
                                  "bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300",
                                gap.severity === "HIGH" &&
                                  "bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300",
                                gap.severity === "MEDIUM" &&
                                  "bg-muted text-muted-foreground",
                                gap.severity === "LOW" && "bg-muted text-muted-foreground"
                              )}
                            >
                              {gap.severity}
                            </span>
                          </div>
                          <p className="text-xs font-medium leading-snug">{gap.message}</p>
                          <p className="text-[10px] text-muted-foreground mt-0.5 leading-relaxed">
                            {gap.remediationHint}
                          </p>
                        </div>
                      </div>
                      <Link
                        href={CTA_HREF[gap.dimension] ?? "/systems"}
                        className="inline-flex h-7 shrink-0 items-center rounded-lg border border-border bg-background px-2.5 text-[10px] font-medium hover:bg-muted"
                      >
                        Open
                        <ArrowRight className="w-3 h-3 ml-1" />
                      </Link>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

        </>
      )}

      <AuAdmCard />
    </div>
  );
}
