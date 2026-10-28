"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { DecisionBadge } from "@/components/decision-badge";
import { RiskBadge } from "@/components/risk-badge";
import { SectorPackBadge } from "@/components/sector-pack-badge";
import { useDashboard } from "@/context/dashboard-context";
import type { PublicClaimsArtifacts, PublicClaimsTeaser } from "@/lib/public-claims";
import type { RiskClass } from "@/lib/types";
import { normaliseDecision } from "@/lib/utils";
import { Download, ExternalLink, ShieldAlert } from "lucide-react";

function downloadText(filename: string, contents: string, type: string) {
  const blob = new Blob([contents], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

async function downloadArtifacts(slug: string, files: PublicClaimsArtifacts) {
  downloadText(
    `${slug}-model_card.json`,
    JSON.stringify(files.model_card, null, 2),
    "application/json",
  );
  downloadText(
    `${slug}-approval.json`,
    JSON.stringify(files.approval, null, 2),
    "application/json",
  );
  downloadText(
    `${slug}-deployment.json`,
    JSON.stringify(files.deployment, null, 2),
    "application/json",
  );
  downloadText(`${slug}-dataset_manifest.csv`, files.dataset_manifest_csv, "text/csv");
}

function riskFromTeaser(value: string | null): RiskClass {
  const lower = (value ?? "limited").toLowerCase();
  if (lower === "high" || lower === "limited" || lower === "minimal" || lower === "prohibited") {
    return lower;
  }
  return "limited";
}

function TeaserCard({ teaser }: { teaser: PublicClaimsTeaser }) {
  const { openSystemDetails, allSystems } = useDashboard();
  const quote = teaser.sources[0]?.quote;
  const registered = teaser.registeredSystemId
    ? allSystems.find((system) => system.id === teaser.registeredSystemId)
    : undefined;

  return (
    <Card>
      <CardHeader className="space-y-2">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-sm">{teaser.legalName}</CardTitle>
            <CardDescription>
              {teaser.hq} · {teaser.systemName}
            </CardDescription>
          </div>
          {teaser.releaseDecision && (
            <DecisionBadge
              decision={normaliseDecision(teaser.releaseDecision)}
              className="flex-shrink-0"
            />
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          <RiskBadge risk={riskFromTeaser(teaser.riskClass)} />
          <SectorPackBadge sector={teaser.sector} />
          <span className="text-[10px] uppercase tracking-wider text-amber-700 dark:text-amber-400 font-semibold">
            Public claims
          </span>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-xs text-muted-foreground leading-relaxed">{teaser.purpose}</p>
        {quote && (
          <blockquote className="text-[11px] leading-relaxed border-l-2 border-amber-500/60 pl-3 text-muted-foreground italic">
            {quote}
          </blockquote>
        )}
        <p className="text-[10px] text-muted-foreground leading-relaxed">{teaser.riskBasis}</p>
        <ul className="text-[10px] text-muted-foreground space-y-1">
          {teaser.sources.map((source) => (
            <li key={source.url + source.title}>
              <a
                href={source.url}
                target="_blank"
                rel="noreferrer"
                className="text-primary underline inline-flex items-center gap-1"
              >
                {source.title}
                <ExternalLink className="w-3 h-3" />
              </a>
              <span> · retrieved {source.retrievedAt}</span>
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap gap-2 pt-1">
          <Button
            size="sm"
            variant="outline"
            onClick={async () => {
              const files = await api.publicClaims.evgraph(teaser.slug);
              await downloadArtifacts(teaser.slug, files);
            }}
          >
            <Download className="w-3 h-3 mr-1" />
            Evgraph JSON/CSV
          </Button>
          {registered && (
            <Button size="sm" variant="outline" onClick={() => openSystemDetails(registered.id)}>
              Open in registry
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export default function PublicClaimsPage() {
  const query = useQuery({
    queryKey: ["public-claims"],
    queryFn: api.publicClaims.list,
  });

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-amber-200/80 dark:border-amber-900/40 bg-amber-50/40 dark:bg-amber-950/15 px-4 py-3 space-y-2">
        <p className="text-[11px] font-semibold text-foreground flex items-center gap-1.5">
          <ShieldAlert className="w-3.5 h-3.5 text-amber-700" />
          Public-claims teasers — not customers
        </p>
        <p className="text-[10px] text-muted-foreground leading-relaxed">
          {query.data?.disclaimer ??
            "Named organisations are reconstructed from public pages only. Not a legal finding, not an accusation, not a notified-body assessment."}
        </p>
        <p className="text-[10px] text-muted-foreground leading-relaxed">
          Evgraph is a library. From each pack:{" "}
          <code className="text-[10px]">{query.data?.howtoPromotion}</code>
          {query.data?.howtoDataset ? (
            <>
              {" "}
              then <code className="text-[10px]">{query.data.howtoDataset}</code>
            </>
          ) : null}
        </p>
        <p className="text-[10px] text-muted-foreground">
          Retrieved {query.data?.retrievedAt ?? "—"}. Seeding{" "}
          {query.data?.seedingEnabled ? "on (local demo)" : "off (catalog only)"}.{" "}
          <Link href="/systems" className="text-primary underline">
            Registry
          </Link>
        </p>
      </div>

      {query.isError && (
        <p className="text-xs text-red-700">
          Could not load public-claims catalog. Start the API with authentication.
        </p>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {(query.data?.systems ?? []).map((teaser) => (
          <TeaserCard key={teaser.slug} teaser={teaser} />
        ))}
      </div>
    </div>
  );
}
