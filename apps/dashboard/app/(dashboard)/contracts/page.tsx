"use client";

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { ContractCard } from "@/components/contract-card";
import { LineageGraph } from "@/components/lineage-graph";
import { useContracts } from "@/hooks/use-contracts";
import { allowMockFallback } from "@/lib/live-mode";
import { MOCK_CONTRACTS, MOCK_DRIFT_EVENTS } from "@/lib/mock-data";
import { useState } from "react";
import { useQueries, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import type { DriftEvent } from "@/lib/types";
import { isLiveEntityId } from "@/lib/ids";

import { useDashboard } from "@/context/dashboard-context";

const INPUT =
  "w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50";

export default function ContractsPage() {
  const { allSystems: systems } = useDashboard();
  const { data: contracts = allowMockFallback() ? MOCK_CONTRACTS : [] } = useContracts();
  const qc = useQueryClient();
  const liveSystems = systems.filter((s) => isLiveEntityId(s.id));
  const [systemId, setSystemId] = useState("");
  const [name, setName] = useState("");
  const [owner, setOwner] = useState("");
  const [version, setVersion] = useState("");
  const [coverage, setCoverage] = useState(100);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleCreate(e: React.SyntheticEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await api.contracts.create({
        systemId: systemId || liveSystems[0]?.id || "", name: name.trim(), owner: owner.trim(),
        version: version.trim(), coverage,
      });
      setName(""); setOwner(""); setVersion("");
      await qc.invalidateQueries({ queryKey: ["contracts"] });
      await qc.invalidateQueries({ queryKey: ["systems"] });
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not register the contract.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDriftAction(event: DriftEvent, status: "ACKNOWLEDGED" | "RESOLVED", note: string) {
    await api.contracts.updateDriftEvent(event.contractId, event.id, status, note);
    await qc.invalidateQueries({ queryKey: ["drift-events", event.contractId] });
    await qc.invalidateQueries({ queryKey: ["contracts"] });
    await qc.invalidateQueries({ queryKey: ["systems"] });
  }

  const driftQueries = useQueries({
    queries: contracts.map((contract) => ({
      queryKey: ["drift-events", contract.id],
      queryFn: () => api.contracts.driftEvents(contract.id),
      // Skip API for demo mock contract ids (they 400 on the real backend).
      enabled: isLiveEntityId(contract.id),
      placeholderData: allowMockFallback()
        ? MOCK_DRIFT_EVENTS.filter((e) => e.contractId === contract.id)
        : undefined,
    })),
  });

  const allDriftEvents: DriftEvent[] = driftQueries.flatMap((q) => q.data ?? []);

  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">
        Article 72 stand-in until 2 Sep 2027: contract drift. The Commission template is not included.
      </p>
      {liveSystems.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Register a data contract</CardTitle>
            <CardDescription>
              The schema and quality checks run in your pipeline. It reports drift to the contract; a breach blocks
              the release until compliance resolves it with a note.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreate} className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label htmlFor="contract-system" className="text-xs font-medium text-muted-foreground">AI system</label>
                <select id="contract-system" className={INPUT} value={systemId || liveSystems[0]?.id}
                  onChange={(e) => setSystemId(e.target.value)}>
                  {liveSystems.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label htmlFor="contract-name" className="text-xs font-medium text-muted-foreground">Contract name</label>
                <input id="contract-name" required className={INPUT} value={name} onChange={(e) => setName(e.target.value)}
                  placeholder="Applicant feature set" />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="contract-owner" className="text-xs font-medium text-muted-foreground">Owner</label>
                <input id="contract-owner" required className={INPUT} value={owner} onChange={(e) => setOwner(e.target.value)}
                  placeholder="Data Platform" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label htmlFor="contract-version" className="text-xs font-medium text-muted-foreground">Version</label>
                  <input id="contract-version" required className={INPUT} value={version}
                    onChange={(e) => setVersion(e.target.value)} placeholder="2026-10" />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="contract-coverage" className="text-xs font-medium text-muted-foreground">Field coverage (%)</label>
                  <input id="contract-coverage" type="number" min={0} max={100} className={INPUT} value={coverage}
                    onChange={(e) => setCoverage(Number(e.target.value))} />
                </div>
              </div>
              {formError && <p role="alert" className="col-span-2 text-xs text-destructive">{formError}</p>}
              <div className="col-span-2">
                <Button type="submit" disabled={saving}>{saving ? "Registering…" : "Register contract"}</Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="grid grid-cols-2 gap-4">
        {contracts.map((contract) => {
          const events = allDriftEvents.filter((e) => e.contractId === contract.id);
          return (
            <ContractCard
              key={contract.id}
              contract={contract}
              driftEvents={events}
              onDriftAction={isLiveEntityId(contract.id) ? handleDriftAction : undefined}
            />
          );
        })}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Data Lineage</CardTitle>
          <CardDescription>
            Data sources → contracts → AI systems. Animated edges indicate active drift.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <LineageGraph systems={systems} contracts={contracts} />
        </CardContent>
      </Card>
    </div>
  );
}
