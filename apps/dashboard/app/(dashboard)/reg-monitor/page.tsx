"use client";

import { useMemo, useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { useDashboard } from "@/context/dashboard-context";
import {
  useMarkRegItemReviewed,
  useRegMonitorItems,
  useRegMonitorRelevant,
} from "@/hooks/use-reg-monitor";
import { cn, formatIsoStamp } from "@/lib/utils";
import type { RegItem } from "@/lib/types";
import {
  AlertTriangle,
  CheckCircle2,
  ExternalLink,
  Newspaper,
  RefreshCw,
} from "lucide-react";

type ViewMode = "all" | "relevant" | "unreviewed";

export default function RegMonitorPage() {
  const { allSystems } = useDashboard();
  const [systemId, setSystemId] = useState(allSystems[0]?.id ?? "");
  const [view, setView] = useState<ViewMode>("all");
  const [notes, setNotes] = useState<Record<string, string>>({});

  const feedQuery = useRegMonitorItems();
  const relevantQuery = useRegMonitorRelevant(systemId || null);
  const markReviewed = useMarkRegItemReviewed();

  const feed = view === "relevant" ? relevantQuery.data : feedQuery.data;
  const isFetching = view === "relevant" ? relevantQuery.isFetching : feedQuery.isFetching;

  const items = useMemo(() => {
    const list = feed?.items ?? [];
    if (view === "unreviewed") {
      return list.filter((i) => !i.reviewed);
    }
    return list;
  }, [feed, view]);

  async function handleReview(item: RegItem) {
    try {
      await markReviewed.mutateAsync({ itemId: item.id, notes: notes[item.id] });
    } catch {
      // offline / mock — optimistically leave UI; mutation may fail against mock ids
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Article 72 stand-in until 2 Sep 2027: reg-monitor diff. The Commission template is not included.
      </p>
      <div className="rounded-xl border border-amber-200/80 dark:border-amber-900/40 bg-amber-50/40 dark:bg-amber-950/15 px-4 py-3">
        <p className="text-[11px] font-semibold text-foreground mb-0.5 flex items-center gap-1.5">
          <Newspaper className="w-3.5 h-3.5" />
          Regulatory change monitoring
        </p>
        <p className="text-[10px] text-muted-foreground leading-relaxed">
          Assistive <strong className="font-semibold text-foreground">near-real-time polled</strong> feed
          (interval-bound, not continuous legal push). This is{" "}
          <strong className="font-semibold text-foreground">not an official legal bulletin</strong>, not
          real-time law, and not legal advice. Impact hints map keywords to control/obligation codes and
          prefer <span className="font-mono text-[10px]">UNCERTAIN</span>. The feed{" "}
          <strong className="font-semibold text-foreground">never auto-changes risk class or control status</strong>.
        </p>
        {feed?.latencyNote && (
          <p className="text-[10px] text-muted-foreground mt-1.5 flex items-start gap-1.5">
            <AlertTriangle className="w-3 h-3 mt-0.5 flex-shrink-0 text-amber-700" />
            {feed.latencyNote}
          </p>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1.5">
          <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
            View
          </label>
          <div className="flex gap-1.5">
            {(
              [
                ["all", "All items"],
                ["unreviewed", "Unreviewed"],
                ["relevant", "Per-system relevant"],
              ] as const
            ).map(([key, label]) => (
              <Button
                key={key}
                size="sm"
                variant={view === key ? "default" : "outline"}
                className="h-9 text-[11px]"
                onClick={() => setView(key)}
              >
                {label}
              </Button>
            ))}
          </div>
        </div>

        {view === "relevant" && (
          <div className="space-y-1.5 min-w-[220px]">
            <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
              AI system
            </label>
            <Select value={systemId} onValueChange={(v) => setSystemId(v ?? "")}>
              <SelectTrigger aria-label="AI system" className="h-9 text-xs">
                <SelectValue placeholder="Select system" />
              </SelectTrigger>
              <SelectContent>
                {allSystems.map((s) => (
                  <SelectItem key={s.id} value={s.id} className="text-xs">
                    {s.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <Button
          size="sm"
          variant="outline"
          className="h-9 text-[11px]"
          disabled={isFetching}
          onClick={() => {
            void feedQuery.refetch();
            if (systemId) void relevantQuery.refetch();
          }}
        >
          <RefreshCw className={cn("w-3.5 h-3.5 mr-1.5", isFetching && "animate-spin")} />
          Refresh
        </Button>
      </div>

      <div className="grid gap-3">
        {items.length === 0 && (
          <Card>
            <CardContent className="py-8 text-center text-sm text-muted-foreground">
              No regulatory feed items in this view. Bootstrap fixtures load when the API starts
              with network blocked.
            </CardContent>
          </Card>
        )}

        {items.map((item) => (
          <Card key={item.id} className="overflow-hidden">
            <CardHeader className="pb-2">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <CardTitle className="text-sm leading-snug">{item.title}</CardTitle>
                  <CardDescription className="text-[11px] mt-1 flex flex-wrap gap-x-3 gap-y-0.5">
                    <span className="font-mono">{item.sourceCode}</span>
                    {item.publishedAt && (
                      <span>Published {item.publishedAt.slice(0, 10)}</span>
                    )}
                    <span suppressHydrationWarning>
                      Fetched {formatIsoStamp(item.fetchedAt)}
                    </span>
                  </CardDescription>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  {item.reviewed ? (
                    <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-900 rounded-full px-2 py-0.5">
                      <CheckCircle2 className="w-3 h-3" />
                      Reviewed
                    </span>
                  ) : (
                    <span className="inline-flex items-center text-[10px] font-semibold text-amber-800 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900 rounded-full px-2 py-0.5">
                      Needs review
                    </span>
                  )}
                  <a
                    href={item.url}
                    target="_blank"
                    rel="noreferrer"
                    className="text-muted-foreground hover:text-primary"
                    title="Open source URL"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                  </a>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-xs text-muted-foreground leading-relaxed">{item.summary}</p>

              {item.relevanceReason && (
                <p className="text-[11px] rounded-lg border border-border bg-muted/30 px-2.5 py-1.5">
                  <span className="font-semibold text-foreground">Relevance: </span>
                  {item.relevanceReason}
                </p>
              )}

              <div className="flex flex-wrap gap-1.5">
                {item.impactHints.map((h) => (
                  <span
                    key={h.id}
                    className="inline-flex items-center gap-1 text-[10px] font-medium rounded-md border border-border bg-card px-2 py-1"
                    title={h.impactNote}
                  >
                    <span className="font-mono text-amber-700 dark:text-amber-400">{h.impactLevel}</span>
                    {h.controlCode && (
                      <span className="text-muted-foreground">ctrl:{h.controlCode}</span>
                    )}
                    {h.obligationCode && (
                      <span className="text-muted-foreground">obl:{h.obligationCode}</span>
                    )}
                  </span>
                ))}
              </div>

              {!item.reviewed && (
                <div className="flex flex-wrap items-end gap-2 pt-1 border-t border-border">
                  <div className="flex-1 min-w-[200px] space-y-1">
                    <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                      Review notes
                    </label>
                    <input
                      aria-label="Review notes"
                      className="w-full h-9 text-xs rounded-lg border border-border bg-background px-2.5"
                      placeholder="Human triage note (does not change risk/controls)"
                      value={notes[item.id] ?? ""}
                      onChange={(e) =>
                        setNotes((prev) => ({ ...prev, [item.id]: e.target.value }))
                      }
                    />
                  </div>
                  <Button
                    size="sm"
                    className="h-9 text-[11px]"
                    disabled={markReviewed.isPending}
                    onClick={() => void handleReview(item)}
                  >
                    Mark reviewed
                  </Button>
                </div>
              )}
              {item.reviewed && item.reviewNotes && (
                <p className="text-[11px] text-muted-foreground">
                  Review notes: {item.reviewNotes}
                </p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
