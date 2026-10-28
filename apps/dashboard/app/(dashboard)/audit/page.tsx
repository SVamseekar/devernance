"use client";

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { AUDIT_PAGE_SIZE } from "@/lib/api";
import { formatDate } from "@/lib/utils";
import { ShieldCheck, FileText, AlertTriangle, CheckCircle2 } from "lucide-react";
import { useDashboard } from "@/context/dashboard-context";

const EVENT_CONFIG: Record<string, { icon: React.ElementType; color: string; bg: string }> = {
  RELEASE_GATE_CALCULATED: { icon: ShieldCheck,   color: "text-indigo-600 dark:text-indigo-400",  bg: "bg-indigo-50 dark:bg-indigo-950/50" },
  EVIDENCE_INDEXED:        { icon: FileText,       color: "text-sky-600 dark:text-sky-400",        bg: "bg-sky-50 dark:bg-sky-950/50" },
  EVIDENCE_QUERIED:        { icon: FileText,       color: "text-sky-600 dark:text-sky-400",        bg: "bg-sky-50 dark:bg-sky-950/50" },
  DRIFT_EVENT_CREATED:     { icon: AlertTriangle,  color: "text-amber-700 dark:text-amber-400",    bg: "bg-amber-50 dark:bg-amber-950/50" },
  CONTRACT_UPDATED:        { icon: CheckCircle2,   color: "text-emerald-700 dark:text-emerald-400",bg: "bg-emerald-50 dark:bg-emerald-950/50" },
};
const DEFAULT_CFG = { icon: ShieldCheck, color: "text-muted-foreground", bg: "bg-muted" };

const ACTOR_NAMES: Record<string, string> = {
  "actor-priya": "Priya Nair",
  "actor-marco": "Marco Bianchi",
  "actor-leo":   "Leo Hartmann",
  "actor-sofia": "Sofia Andersen",
};

function toTitle(s: string) {
  return s.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function PayloadSummary({ eventType, payload }: { eventType: string; payload: Record<string, unknown> }) {
  if (eventType === "RELEASE_GATE_CALCULATED") {
    const decision = String(payload.decision ?? "");
    const color = decision === "PASS"
      ? "text-emerald-700 dark:text-emerald-400"
      : decision === "BLOCKED"
      ? "text-red-700 dark:text-red-400"
      : "text-amber-700 dark:text-amber-400";
    return (
      <p className="text-xs text-muted-foreground mt-0.5">
        Decision: <span className={`font-semibold ${color}`}>{decision}</span>
        {payload.reason != null && <span> · {String(payload.reason)}</span>}
      </p>
    );
  }
  if (eventType === "DRIFT_EVENT_CREATED") {
    const sev = String(payload.severity ?? "");
    const sevColor = sev === "BREACH" ? "text-red-700 dark:text-red-400" : "text-amber-700 dark:text-amber-400";
    return (
      <p className="text-xs text-muted-foreground mt-0.5">
        <span className={`font-semibold ${sevColor}`}>{sev}</span>
        {" on "}
        <span className="font-mono text-[11px]">{String(payload.field ?? "")}</span>
        {payload.contractName != null && <span> · {String(payload.contractName)}</span>}
      </p>
    );
  }
  if (eventType === "EVIDENCE_INDEXED") {
    return (
      <p className="text-xs text-muted-foreground mt-0.5">
        {String(payload.title ?? "")}
        {payload.type != null && <span> · {String(payload.type)}</span>}
        {payload.chunks !== undefined && <span> · {String(payload.chunks)} chunks</span>}
      </p>
    );
  }
  if (eventType === "EVIDENCE_QUERIED") {
    return (
      <p className="text-xs text-muted-foreground mt-0.5 italic truncate max-w-sm">
        &ldquo;{String(payload.question ?? "")}&rdquo;
      </p>
    );
  }
  return null;
}

export default function AuditPage() {
  const { allAudits: events } = useDashboard();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Immutable Audit Ledger</CardTitle>
        <CardDescription>
          Append-only record of evidence checks, approvals, overrides, and release decisions.
          {events.length >= AUDIT_PAGE_SIZE &&
            ` Showing the latest ${AUDIT_PAGE_SIZE} events; the workspace export in Settings has the full trail.`}
        </CardDescription>
      </CardHeader>
      <CardContent className="px-0 pb-0">
        <div className="divide-y divide-border">
          {events.map((event) => {
            const cfg = EVENT_CONFIG[event.eventType] ?? DEFAULT_CFG;
            const Icon = cfg.icon;
            return (
              <div key={event.id} className="flex items-start gap-4 px-6 py-4 hover:bg-muted/20 transition-colors last:rounded-b-xl">
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5 ${cfg.bg}`}>
                  <Icon className={`w-4 h-4 ${cfg.color}`} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium">{toTitle(event.eventType)}</p>
                    {event.actorId && (
                      <span className="text-xs text-muted-foreground">· {ACTOR_NAMES[event.actorId] ?? event.actorId}</span>
                    )}
                  </div>
                  <PayloadSummary eventType={event.eventType} payload={event.payload as Record<string, unknown>} />
                </div>
                <time className="text-xs text-muted-foreground shrink-0 mt-0.5 tabular-nums" suppressHydrationWarning>
                  {formatDate(event.createdAt)}
                </time>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
