"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { SystemCard } from "@/components/system-card";
import { ReleaseGateTable } from "@/components/release-gate-table";
import { useDashboard } from "@/context/dashboard-context";
import { RegisterSystemModal } from "@/components/register-system-modal";
import { ObligationMapWizard } from "@/components/obligation-map-wizard";
import { Button } from "@/components/ui/button";
import { Plus, Scale } from "lucide-react";
import Link from "next/link";
import { isPublicClaimsSystem } from "@/lib/public-claims";

export default function SystemsPage() {
  const { allSystems } = useDashboard();
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [obligationSystemId, setObligationSystemId] = useState<string | null>(null);

  const obligationSystem = allSystems.find((s) => s.id === obligationSystemId) ?? null;

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-amber-200/80 dark:border-amber-900/40 bg-amber-50/40 dark:bg-amber-950/15 px-4 py-3">
        <p className="text-[11px] font-semibold text-foreground mb-0.5">
          Assisted obligation determination
        </p>
        <p className="text-[10px] text-muted-foreground leading-relaxed">
          Open a system and run the obligation map wizard for a{" "}
          <strong className="font-semibold text-foreground">suggested applicability / obligation map</strong>
          {" "}(ruleset v2 includes Art. 5 screens and Art. 50). Not legal advice; requires human
          legal review. Export Evgraph JSON from a system card for{" "}
          <code className="text-[10px]">evgraph scan-promotion --gate</code> — the library is not
          bundled in this app.
        </p>
        {allSystems.some(isPublicClaimsSystem) && (
          <p className="text-[10px] text-muted-foreground leading-relaxed mt-2">
            Named EU firms in this registry are{" "}
            <Link href="/public-claims" className="text-primary underline">
              public-claims teasers
            </Link>
            , not customers.
          </p>
        )}
        {allSystems.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-2">
            {allSystems.slice(0, 4).map((system) => (
              <Button
                key={system.id}
                size="sm"
                variant="outline"
                className="h-7 text-[10px]"
                onClick={() => setObligationSystemId(system.id)}
              >
                <Scale className="w-3 h-3 mr-1" />
                {system.name}
              </Button>
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-3 gap-4">
        {/* Dash-border Register Card */}
        <div
          onClick={() => setIsRegisterOpen(true)}
          className="rounded-xl border border-dashed border-border p-4 flex flex-col items-center justify-center text-center cursor-pointer hover:bg-muted/40 hover:border-primary/40 hover:shadow-xs transition-all min-h-36 group bg-muted/10"
        >
          <div className="w-9 h-9 rounded-full bg-primary/15 group-hover:bg-primary/25 flex items-center justify-center transition-colors shrink-0">
            <Plus className="w-4 h-4 text-primary" />
          </div>
          <span className="text-xs font-semibold mt-2.5 text-foreground leading-none">Register System</span>
          <span className="text-[10px] text-muted-foreground mt-1.5 leading-normal max-w-40">
            Run EU AI Act risk questionnaire & obligation mapping
          </span>
        </div>

        {allSystems.map((system) => (
          <SystemCard key={system.id} system={system} />
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Release Gate</CardTitle>
          <CardDescription>
            Combined compliance evidence, eval regression, data drift, and human oversight.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ReleaseGateTable systems={allSystems} />
        </CardContent>
      </Card>

      <RegisterSystemModal
        isOpen={isRegisterOpen}
        onClose={() => setIsRegisterOpen(false)}
        onRegistered={(id) => setObligationSystemId(id)}
      />

      {obligationSystem && (
        <ObligationMapWizard
          isOpen={obligationSystemId !== null}
          onClose={() => setObligationSystemId(null)}
          systemId={obligationSystem.id}
          systemName={obligationSystem.name}
        />
      )}
    </div>
  );
}
