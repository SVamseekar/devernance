"use client";

import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { SYSTEM_EXPORTS, type SystemExportKind } from "@/lib/export-view";

type State = { status: "idle" | "working" | "done" | "upgrade" | "error"; detail?: string };

/** Downloads built from one system's recorded evidence: questionnaire answers and the GRC ZIP. */
export function SystemExportsPanel({ systemId }: { systemId: string }) {
  const [states, setStates] = useState<Partial<Record<SystemExportKind, State>>>({});

  async function download(kind: SystemExportKind) {
    setStates((s) => ({ ...s, [kind]: { status: "working" } }));
    try {
      const { filename } = await api.systems.downloadExport(systemId, kind);
      setStates((s) => ({ ...s, [kind]: { status: "done", detail: filename } }));
    } catch (error) {
      const upgrade = error instanceof ApiError && error.status === 402;
      setStates((s) => ({ ...s, [kind]: { status: upgrade ? "upgrade" : "error" } }));
    }
  }

  return (
    <div className="space-y-3">
      {SYSTEM_EXPORTS.map((item) => {
        const state = states[item.kind] ?? { status: "idle" };
        return (
          <div key={item.kind} className="rounded-lg border border-border bg-muted/20 p-3 text-xs">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold text-foreground">{item.label}</p>
                <p className="mt-1 text-muted-foreground">{item.description}</p>
              </div>
              <button
                type="button"
                onClick={() => download(item.kind)}
                disabled={state.status === "working"}
                className="shrink-0 rounded-md border border-border bg-background px-3 py-1.5 font-medium text-foreground hover:bg-muted disabled:opacity-60"
              >
                {state.status === "working" ? "Preparing…" : item.button}
              </button>
            </div>
            <p className="mt-2 text-muted-foreground" role="status" aria-live="polite">
              {state.status === "done" && <>Downloaded {state.detail}. Review it before you share it.</>}
              {state.status === "error" && (
                <span className="text-destructive">Could not prepare the download. Try again in a moment.</span>
              )}
              {state.status === "upgrade" && (
                <>
                  {item.upgradeText}.{" "}
                  <Link href="/settings#billing" className="text-primary underline-offset-4 hover:underline">
                    See plans
                  </Link>
                </>
              )}
            </p>
          </div>
        );
      })}
    </div>
  );
}
