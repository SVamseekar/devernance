"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";
import { FRAMEWORK_PENDING_TEXT, FRAMEWORK_UPGRADE_TEXT, groupByFunction, statusLabel } from "@/lib/framework-view";
import type { FrameworkItemStatus } from "@/lib/types";

const CHIP: Record<FrameworkItemStatus["status"], string> = {
  covered: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/30",
  partial: "bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/30",
  no_evidence: "bg-muted text-muted-foreground border-border",
};

interface FrameworksPanelProps {
  systemId: string;
  framework?: string;
  label?: string;
  upgradeText?: string;
}

/** Framework crosswalk for one system: which evidence is relevant to each item. Never a conformity verdict. */
export function FrameworksPanel({
  systemId,
  framework = "nist-ai-rmf",
  label = "NIST AI RMF 1.0",
  upgradeText = FRAMEWORK_UPGRADE_TEXT,
}: FrameworksPanelProps) {
  const view = useQuery({
    queryKey: ["framework", systemId, framework],
    queryFn: () => api.systems.framework(systemId, framework),
    retry: false,
  });

  if (view.isLoading) {
    return <p className="text-xs text-muted-foreground">Loading {label}…</p>;
  }
  if (view.error instanceof ApiError && view.error.status === 402) {
    return (
      <div className="rounded-lg border border-border bg-muted/20 p-4 text-xs">
        <p className="font-semibold text-foreground">{label}</p>
        <p className="mt-1 text-muted-foreground">
          {upgradeText}.{" "}
          <Link href="/settings#billing" className="text-primary underline-offset-4 hover:underline">
            See plans
          </Link>
        </p>
      </div>
    );
  }
  if (view.error instanceof ApiError && view.error.status === 404) {
    // The mapping exists but has not been reviewed against its source yet, so it is not served.
    return (
      <div className="rounded-lg border border-border bg-muted/20 p-4 text-xs">
        <p className="font-semibold text-foreground">{label}</p>
        <p className="mt-1 text-muted-foreground">{FRAMEWORK_PENDING_TEXT}</p>
      </div>
    );
  }
  if (view.error || !view.data) {
    return <p className="text-xs text-destructive">Could not load {label}. Try again in a moment.</p>;
  }

  const data = view.data;
  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border bg-muted/20 p-3 text-xs">
        <p className="font-semibold text-foreground">
          {label} {data.type === "checklist" ? "checklist" : "crosswalk"}
          {!data.reviewed && <span className="ml-2 font-normal text-muted-foreground">(mapping pending review)</span>}
        </p>
        <p className="mt-1 text-muted-foreground">{data.disclaimer}</p>
        <a href={data.source} target="_blank" rel="noreferrer" className="mt-1 inline-block text-primary underline-offset-4 hover:underline">
          Source
        </a>
      </div>
      {groupByFunction(data.items).map((group) => (
        <section key={group.name}>
          <h4 className="mb-2 flex items-center justify-between text-xs font-semibold uppercase tracking-widest text-foreground">
            {group.name}
            <span className="font-normal normal-case tracking-normal text-muted-foreground">
              {group.covered} of {group.items.length} with evidence linked
            </span>
          </h4>
          <ul className="space-y-2">
            {group.items.map((item) => (
              <li key={item.id} className="rounded-lg border border-border p-3">
                <div className="flex items-start justify-between gap-3">
                  <p className="text-xs text-foreground">
                    <span className="font-semibold">{item.id}</span> · {item.title}
                  </p>
                  <span className={cn("shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium", CHIP[item.status])}>
                    {statusLabel(item.status)}
                  </span>
                </div>
                <p className="mt-1.5 text-[11px] text-muted-foreground">
                  Controls: {item.requiredControls.map((c) => (item.controls.includes(c) ? `${c} ✓` : c)).join(", ") || "none"}
                  {" · "}
                  Evidence: {item.requiredEvidence.map((e) => (item.evidence.includes(e) ? `${e} ✓` : e)).join(", ") || "none"}
                  {item.sourceUrl && (
                    <>
                      {" · "}
                      <a href={item.sourceUrl} target="_blank" rel="noreferrer" className="text-primary hover:underline">
                        Statute
                      </a>
                    </>
                  )}
                </p>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
