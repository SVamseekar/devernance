"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { RiskTopology } from "@/components/risk-topology";
import { useContracts } from "@/hooks/use-contracts";
import { useOpenWorkflows } from "@/hooks/use-open-workflows";
import { useSystems } from "@/hooks/use-systems";
import { api, ApiError } from "@/lib/api";
import { firstPendingStage } from "@/lib/workflow-helpers";
import { allowMockFallback } from "@/lib/live-mode";
import { MOCK_CONTRACTS } from "@/lib/mock-data";
import { normaliseDecision, cn, formatDate } from "@/lib/utils";
import {
  TrendingUp, TrendingDown, Minus,
  ShieldAlert, AlertTriangle, CheckCircle2, Clock,
  ShieldCheck, FileText, GitBranch
} from "lucide-react";
import { DecisionBadge } from "@/components/decision-badge";
import { useDashboard } from "@/context/dashboard-context";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { ApiStatusPill } from "@/components/api-status-pill";
import { CertificationReadinessCard } from "@/components/certification-readiness-card";

const ACTOR_NAMES: Record<string, string> = {
  "actor-priya": "Priya Nair",
  "actor-marco": "Marco Bianchi",
  "actor-leo":   "Leo Hartmann",
  "actor-sofia": "Sofia Andersen",
};

const EVENT_ICON: Record<string, React.ElementType> = {
  RELEASE_GATE_CALCULATED: ShieldCheck,
  EVIDENCE_INDEXED:        FileText,
  EVIDENCE_QUERIED:        FileText,
  DRIFT_EVENT_CREATED:     AlertTriangle,
};

const EVENT_COLOR: Record<string, string> = {
  RELEASE_GATE_CALCULATED: "text-indigo-500",
  EVIDENCE_INDEXED:        "text-sky-500",
  EVIDENCE_QUERIED:        "text-sky-500",
  DRIFT_EVENT_CREATED:     "text-amber-500",
};

function toTitle(s: string) {
  return s.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function CommandPage() {
  const { allSystems: systems, openSystemDetails, allAudits: auditEvents } =
    useDashboard();
  const qc = useQueryClient();
  const [overrideError, setOverrideError] = useState<string | null>(null);
  const [overriding, setOverriding] = useState(false);
  const { data: contracts = allowMockFallback() ? MOCK_CONTRACTS : [] } = useContracts();
  const { data: openWorkflows = [] } = useOpenWorkflows();
  const { isError: systemsError, isSuccess: systemsSuccess, data: systemsData } = useSystems();
  // Same heuristic as Evidence: live API when systems query succeeds with real IDs
  const apiOnline =
    systemsSuccess &&
    !systemsError &&
    Array.isArray(systemsData) &&
    systemsData.length > 0 &&
    !String(systemsData[0]?.id ?? "").startsWith("mock-");

  const [riskFilter, setRiskFilter] = useState<"all" | "high" | "limited" | "minimal">("all");

  // Override Modal States
  const [overrideSystemId, setOverrideSystemId] = useState<string | null>(null);
  const [overrideSystemName, setOverrideSystemName] = useState("");
  const [justification, setJustification] = useState("");

  async function confirmOverride(systemId: string, rationale: string) {
    setOverriding(true);
    setOverrideError(null);
    try {
      const workflow = await api.workflows.active(systemId);
      const stage = workflow ? firstPendingStage(workflow) : null;
      if (!workflow || !stage) {
        setOverrideError("There is no open approval stage to override for this system.");
        return;
      }
      await api.workflows.override(systemId, workflow.id, stage.id, rationale);
      await Promise.all([
        qc.invalidateQueries({ queryKey: ["systems"] }),
        qc.invalidateQueries({ queryKey: ["audit-events"] }),
      ]);
      setOverrideSystemId(null);
      setJustification("");
    } catch (err) {
      setOverrideError(err instanceof ApiError ? err.message : "Override failed.");
    } finally {
      setOverriding(false);
    }
  }

  const blocked   = systems.filter((s) => normaliseDecision(s.releaseDecision) === "Blocked");
  const review    = systems.filter((s) => normaliseDecision(s.releaseDecision) === "Review");
  const passing   = systems.filter((s) => normaliseDecision(s.releaseDecision) === "Pass");
  const highRisk  = systems.filter((s) => s.riskClass === "high").length;
  const avgEval   = systems.length
    ? Math.round(systems.reduce((sum, s) => sum + s.evalScore, 0) / systems.length)
    : 0;
  const avgEvidence = systems.length
    ? Math.round(systems.reduce((sum, s) => sum + s.evidenceCoverage, 0) / systems.length)
    : 0;
  const breaches  = contracts.filter((c) => c.status === "BREACH").length;
  const warnings  = contracts.filter((c) => c.status === "WARNING").length;

  const metrics = [
    {
      label: "AI Systems Registered",
      value: systems.length,
      sub: `${highRisk} high-risk · ${systems.length - highRisk} limited/minimal`,
      trend: "neutral" as const,
    },
    {
      label: "Release Gate Decisions",
      value: `${blocked.length} / ${review.length} / ${passing.length}`,
      sub: "Blocked · Review · Pass",
      trend: (blocked.length > 0 ? "down" : review.length > 0 ? "neutral" : "up") as
        | "up"
        | "down"
        | "neutral",
    },
    {
      label: "Open Workflows",
      value: openWorkflows.length,
      sub:
        openWorkflows.length > 0
          ? `${openWorkflows.length} approval cycle${openWorkflows.length === 1 ? "" : "s"} pending`
          : "No open approval cycles",
      trend: (openWorkflows.length > 0 ? "neutral" : "up") as "up" | "down" | "neutral",
    },
    {
      label: "Contract Health",
      value: breaches > 0 ? `${breaches} breach${breaches > 1 ? "es" : ""}` : warnings > 0 ? `${warnings} warning${warnings > 1 ? "s" : ""}` : "All healthy",
      sub: breaches > 0
        ? `${warnings} warning${warnings !== 1 ? "s" : ""} · open breaches block release`
        : `${contracts.length} contracts · avg eval ${avgEval}% · evidence ${avgEvidence}%`,
      trend: (breaches > 0 ? "down" : warnings > 0 ? "neutral" : "up") as "up" | "down" | "neutral",
    },
  ];

  // All open gaps across all systems, sorted worst-first
  const allGaps = systems
    .flatMap((s) => s.openGaps.map((g) => ({ system: s.name, systemId: s.id, riskClass: s.riskClass, gap: g, decision: normaliseDecision(s.releaseDecision) })))
    .sort((a, b) => (a.decision === "Blocked" ? -1 : b.decision === "Blocked" ? 1 : 0));

  const recentEvents = [...auditEvents]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 6);

  // Filter blocked or review systems for override actions
  const blockedOrReviewSystems = systems.filter(
    (s) => normaliseDecision(s.releaseDecision) === "Blocked" || normaliseDecision(s.releaseDecision) === "Review"
  );

  // SVG Donut Calculations
  const high = systems.filter(s => s.riskClass === "high").length;
  const limited = systems.filter(s => s.riskClass === "limited").length;
  const minimal = systems.filter(s => s.riskClass === "minimal").length;
  const total = systems.length;

  const r = 36;
  const circ = 2 * Math.PI * r;
  const highPct = total ? high / total : 0;
  const limitedPct = total ? limited / total : 0;
  const minimalPct = total ? minimal / total : 0;

  const strokeHigh = circ * highPct;
  const strokeLimited = circ * limitedPct;
  const strokeMinimal = circ * minimalPct;

  const offsetHigh = 0;
  const offsetLimited = strokeHigh;
  const offsetMinimal = strokeHigh + strokeLimited;

  function handleTriggerOverride(id: string, name: string) {
    setOverrideSystemId(id);
    setOverrideSystemName(name);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs text-muted-foreground">
            Ops summary — gate decisions, open approvals, contract breaches (from live APIs or demo data).
          </p>
        </div>
        <ApiStatusPill online={apiOnline} />
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-4 gap-4">
        {metrics.map((m) => (
          <Card key={m.label}>
            <CardContent className="pt-5 pb-4 relative overflow-hidden">
              <p className="text-xs text-muted-foreground mb-3">{m.label}</p>
              <p className="text-2xl font-bold tracking-tight mb-2">{m.value}</p>

              <div className="flex items-center gap-1.5">
                {m.trend === "up"      && <TrendingUp  className="w-3 h-3 text-emerald-500 shrink-0" />}
                {m.trend === "down"    && <TrendingDown className="w-3 h-3 text-red-500 shrink-0" />}
                {m.trend === "neutral" && <Minus        className="w-3 h-3 text-muted-foreground shrink-0" />}
                <p className={cn(
                  "text-xs truncate",
                  m.trend === "up"      && "text-emerald-600 dark:text-emerald-400",
                  m.trend === "down"    && "text-red-600 dark:text-red-400",
                  m.trend === "neutral" && "text-muted-foreground"
                )}>
                  {m.sub}
                </p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Certification readiness (score + gaps — not legal certification) */}
      {systems.length > 0 && (
        <div className="grid grid-cols-2 gap-4">
          {systems.slice(0, 2).map((s) => (
            <CertificationReadinessCard
              key={s.id}
              systemId={s.id}
              systemName={s.name}
            />
          ))}
        </div>
      )}

      {/* Open workflows ops strip */}
      {openWorkflows.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm">
              <GitBranch className="w-4 h-4 text-muted-foreground" />
              Open approval workflows
            </CardTitle>
            <CardDescription>
              Pending human oversight cycles that may block high-risk releases.
            </CardDescription>
          </CardHeader>
          <CardContent className="px-0 pb-0">
            <div className="divide-y divide-border max-h-36 overflow-y-auto">
              {openWorkflows.slice(0, 8).map((wf) => {
                const system = systems.find((s) => s.id === wf.systemId);
                return (
                  <div
                    key={wf.id}
                    className="flex items-center justify-between gap-3 px-6 py-2.5 text-xs"
                  >
                    <div className="min-w-0">
                      <p className="font-medium truncate">
                        {system?.name ?? wf.systemId}
                      </p>
                      <p className="text-muted-foreground truncate">
                        {wf.trigger} · {wf.stages?.find((st) => st.status === "PENDING")?.requiredRole
                          ?? "in progress"}
                      </p>
                    </div>
                    <span className="shrink-0 rounded-md bg-amber-50 px-2 py-0.5 font-semibold text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                      OPEN
                    </span>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Row 2: Analytics Donut & Release Overrides widget */}
      <div className="grid grid-cols-3 gap-4">
        {/* Risk Donut Chart */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle>System Risk Distribution</CardTitle>
            <CardDescription>Ratio of registered AI risk classes</CardDescription>
          </CardHeader>
          <CardContent className="flex items-center justify-between pb-6">
            <div className="relative w-28 h-28 flex-shrink-0">
              <svg viewBox="0 0 100 100" className="w-full h-full transform -rotate-90">
                <circle cx="50" cy="50" r={r} fill="transparent" stroke="var(--border)" strokeWidth="8" />
                {strokeMinimal > 0 && (
                  <circle
                    cx="50" cy="50" r={r} fill="transparent"
                    stroke="oklch(0.62 0.18 160)" strokeWidth="8"
                    strokeDasharray={`${strokeMinimal} ${circ}`}
                    strokeDashoffset={-offsetMinimal}
                  />
                )}
                {strokeLimited > 0 && (
                  <circle
                    cx="50" cy="50" r={r} fill="transparent"
                    stroke="oklch(0.72 0.17 55)" strokeWidth="8"
                    strokeDasharray={`${strokeLimited} ${circ}`}
                    strokeDashoffset={-offsetLimited}
                  />
                )}
                {strokeHigh > 0 && (
                  <circle
                    cx="50" cy="50" r={r} fill="transparent"
                    stroke="oklch(0.57 0.22 25)" strokeWidth="8"
                    strokeDasharray={`${strokeHigh} ${circ}`}
                    strokeDashoffset={-offsetHigh}
                  />
                )}
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
                <span className="text-xl font-bold leading-none">{total}</span>
                <span className="text-[8px] text-muted-foreground mt-1 uppercase font-bold tracking-wider">Total</span>
              </div>
            </div>

            <div className="space-y-1.5 pl-4 flex-1">
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-[oklch(0.57_0.22_25)] shrink-0" />
                  <span className="text-muted-foreground font-medium">High</span>
                </div>
                <span className="font-semibold tabular-nums">{high} ({Math.round((high / total) * 100)}%)</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-[oklch(0.72_0.17_55)] shrink-0" />
                  <span className="text-muted-foreground font-medium">Limited</span>
                </div>
                <span className="font-semibold tabular-nums">{limited} ({Math.round((limited / total) * 100)}%)</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-[oklch(0.62_0.18_160)] shrink-0" />
                  <span className="text-muted-foreground font-medium">Minimal</span>
                </div>
                <span className="font-semibold tabular-nums">{minimal} ({Math.round((minimal / total) * 100)}%)</span>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Release Overrides widget */}
        <Card className="col-span-2">
          <CardHeader className="pb-2">
            <CardTitle>Manual Compliance Overrides</CardTitle>
            <CardDescription>Authorize manual overrides to clear blocked release gates.</CardDescription>
          </CardHeader>
          <CardContent className="px-0 pb-0">
            <div className="divide-y divide-border overflow-y-auto min-h-28 max-h-32">
              {blockedOrReviewSystems.length === 0 ? (
                <div className="flex items-center justify-center gap-2 p-7 text-xs text-emerald-600 dark:text-emerald-400">
                  <CheckCircle2 className="w-4 h-4" />
                  All release gates are clear. No overrides needed.
                </div>
              ) : (
                blockedOrReviewSystems.map((s) => (
                  <div key={s.id} className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-muted/10 transition-colors last:rounded-b-xl">
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-semibold text-foreground leading-none mb-1">{s.name}</p>
                      <p className="text-[10px] text-muted-foreground truncate leading-normal">
                        {s.openGaps[0] || "Evaluation threshold breach"}
                        {s.openGaps.length > 1 && ` (+${s.openGaps.length - 1} more)`}
                      </p>
                    </div>
                    <button
                      onClick={() => handleTriggerOverride(s.id, s.name)}
                      className="px-2.5 py-1 text-[9px] font-bold bg-primary hover:bg-primary/95 text-primary-foreground rounded-lg cursor-pointer transition-colors shadow-xs"
                    >
                      Authorize Override
                    </button>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Row 3: Active blockers + Recent activity */}
      <div className="grid grid-cols-[1fr_340px] gap-4">
        {/* Active blockers & gaps */}
        <Card>
          <CardHeader>
            <CardTitle>Active Control Gaps</CardTitle>
            <CardDescription>Open gaps blocking or flagging systems for release review.</CardDescription>
          </CardHeader>
          <CardContent className="px-0 pb-0">
            <div className="divide-y divide-border max-h-[260px] overflow-y-auto">
              {allGaps.map((item, i) => (
                <div
                  key={i}
                  onClick={() => openSystemDetails(item.systemId)}
                  className="flex items-start gap-3 px-6 py-3.5 hover:bg-muted/30 transition-all border border-transparent hover:border-border cursor-pointer last:rounded-b-xl hover:shadow-xs"
                >
                  <div className="mt-0.5 shrink-0">
                    {item.decision === "Blocked"
                      ? <ShieldAlert className="w-4 h-4 text-red-500" />
                      : <AlertTriangle className="w-4 h-4 text-amber-500" />
                    }
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-foreground leading-snug">{item.gap}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">{item.system}</p>
                  </div>
                  <DecisionBadge decision={item.decision} className="shrink-0 mt-0.5" />
                </div>
              ))}
              {allGaps.length === 0 && (
                <div className="flex items-center gap-2 px-6 py-5 text-sm text-emerald-600 dark:text-emerald-400">
                  <CheckCircle2 className="w-4 h-4" />
                  All control gaps resolved.
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Recent activity */}
        <Card>
          <CardHeader>
            <CardTitle>Recent Activity</CardTitle>
            <CardDescription>Latest audit events across all systems.</CardDescription>
          </CardHeader>
          <CardContent className="px-0 pb-0">
            <div className="divide-y divide-border max-h-[260px] overflow-y-auto">
              {recentEvents.map((event) => {
                const Icon = EVENT_ICON[event.eventType] ?? Clock;
                const color = EVENT_COLOR[event.eventType] ?? "text-muted-foreground";
                const actor = event.actorId ? ACTOR_NAMES[event.actorId] ?? event.actorId : null;
                return (
                  <div key={event.id} className="flex items-start gap-3 px-5 py-3 hover:bg-muted/20 transition-colors last:rounded-b-xl">
                    <Icon className={cn("w-3.5 h-3.5 mt-0.5 shrink-0", color)} />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium leading-snug">{toTitle(event.eventType)}</p>
                      <p className="text-xs text-muted-foreground mt-0.5 truncate" suppressHydrationWarning>
                        {actor ? `${actor} · ` : ""}{formatDate(event.createdAt)}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Release readiness table */}
      <Card>
        <CardHeader className="flex-row items-start justify-between gap-4">
          <div>
            <CardTitle>Release Readiness</CardTitle>
            <CardDescription>Evidence coverage, eval score, and release decision per system.</CardDescription>
          </div>
          <Select value={riskFilter} onValueChange={(v) => v && setRiskFilter(v as typeof riskFilter)}>
            <SelectTrigger className="w-32 h-8 text-xs shrink-0">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All systems</SelectItem>
              <SelectItem value="high">High-risk</SelectItem>
              <SelectItem value="limited">Limited-risk</SelectItem>
              <SelectItem value="minimal">Minimal-risk</SelectItem>
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent>
          <RiskTopology systems={systems} filter={riskFilter} />
        </CardContent>
      </Card>

      {/* Override Justification Dialog Modal */}
      <Modal
        isOpen={overrideSystemId !== null}
        onClose={() => setOverrideSystemId(null)}
        title={`Authorize Override — ${overrideSystemName}`}
        description="Only admins can override. The override, your name, and this justification are written to the hash-chained audit ledger."
      >
        <div className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Oversight Justification</label>
            <Textarea
              rows={4}
              value={justification}
              onChange={(e) => setJustification(e.target.value)}
              placeholder="e.g. Reviewed manual safety fallback protocol. Biweekly bias audits scheduled; manual routing fallback verified under Art. 14 guidelines."
            />
          </div>
          {overrideError && <p role="alert" className="text-xs text-destructive">{overrideError}</p>}
          <div className="flex justify-end gap-2.5">
            <Button variant="outline" size="sm" onClick={() => setOverrideSystemId(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              disabled={overriding || justification.trim().length < 20}
              onClick={() => {
                if (overrideSystemId) {
                  void confirmOverride(overrideSystemId, justification);
                }
              }}
            >
              {overriding ? "Overriding…" : "Confirm Override Signature"}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
