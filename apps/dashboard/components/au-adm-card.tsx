"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api, ApiError } from "@/lib/api";
import { admRows, admSummary, AU_ADM_UPGRADE_TEXT } from "@/lib/au-adm-view";
import { cn } from "@/lib/utils";

const CHIP = {
  include: "bg-primary/10 text-primary border-primary/30",
  needs_input: "bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/30",
  not_applicable: "bg-muted text-muted-foreground border-border",
} as const;

const LABEL = { include: "Include", needs_input: "Needs input", not_applicable: "Not applicable" } as const;

/** Readiness card: which systems the AU privacy policy must describe from 10 December 2026, with drafts. */
export function AuAdmCard() {
  const pack = useQuery({ queryKey: ["packs", "au-adm"], queryFn: api.packs.auAdm, retry: false });
  const [copied, setCopied] = useState<string | null>(null);

  async function copy(systemId: string, text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(systemId);
    } catch {
      setCopied(null);
    }
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">AU privacy policy: automated decisions</CardTitle>
        <CardDescription className="text-[10px]">
          Privacy Act APP 1.7–1.9, from 10 December 2026: describe decisions your programs make or substantially
          support using personal information.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 pb-4 text-xs">
        {pack.isLoading && <p className="text-muted-foreground">Loading…</p>}
        {pack.error instanceof ApiError && pack.error.status === 402 && (
          <p className="text-muted-foreground">
            {AU_ADM_UPGRADE_TEXT}.{" "}
            <Link href="/settings#billing" className="text-primary underline-offset-4 hover:underline">
              See plans
            </Link>
          </p>
        )}
        {pack.error && !(pack.error instanceof ApiError && pack.error.status === 402) && (
          <p className="text-destructive">Could not load the disclosure pack. Try again in a moment.</p>
        )}
        {pack.data && (
          <>
            {(() => {
              const s = admSummary(pack.data.systems);
              return (
                <p className="text-muted-foreground">
                  {s.include} to include · {s.needsInput} need input · {s.notApplicable} not applicable
                </p>
              );
            })()}
            <ul className="space-y-2">
              {admRows(pack.data.systems).map((row) => (
                <li key={row.systemId} className="rounded-lg border border-border p-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-medium">{row.name}</span>
                    <span className={cn("rounded-full border px-2 py-0.5 text-[10px] font-medium", CHIP[row.status])}>
                      {LABEL[row.status]}
                    </span>
                  </div>
                  {row.detail && <p className="mt-1 text-[11px] text-muted-foreground">{row.detail}</p>}
                  {row.copyText && (
                    <div className="mt-2 space-y-1.5">
                      <p className="rounded-md bg-muted/40 p-2 text-[11px] leading-relaxed">{row.copyText}</p>
                      <button
                        type="button"
                        onClick={() => copy(row.systemId, row.copyText!)}
                        className="inline-flex h-7 items-center rounded-lg border border-border bg-background px-2.5 text-[10px] font-medium hover:bg-muted"
                      >
                        {copied === row.systemId ? "Copied" : "Copy draft"}
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
            <p className="text-[10px] text-muted-foreground">
              {pack.data.disclaimer}{" "}
              <a href={pack.data.legislation} target="_blank" rel="noreferrer" className="text-primary hover:underline">
                Legislation
              </a>{" "}
              ·{" "}
              <a href={pack.data.source} target="_blank" rel="noreferrer" className="text-primary hover:underline">
                OAIC
              </a>
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}
