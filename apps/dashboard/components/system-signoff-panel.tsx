"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "@/lib/api";
import { allowedControlStatuses, canEditConformity } from "@/lib/governance-permissions";
import type { ConformityDossier, ControlStatus, RiskClass, SystemControl } from "@/lib/types";
import { DecisionBadge } from "./decision-badge";
import { Button } from "./ui/button";
import { normaliseDecision, cn } from "@/lib/utils";

const FIELD =
  "w-full border border-border rounded-md px-2 py-1 text-[11px] bg-background focus:outline-none focus:ring-2 focus:ring-ring/50";

interface SystemSignoffPanelProps {
  systemId: string;
  riskClass: RiskClass;
  role: string | undefined;
}

/** What the gate says and why, the controls behind it, and the Annex IV / FRIA record a high-risk PASS needs. */
export function SystemSignoffPanel({ systemId, riskClass, role }: SystemSignoffPanelProps) {
  const qc = useQueryClient();
  const gate = useQuery({ queryKey: ["release-gate", systemId], queryFn: () => api.systems.releaseGate(systemId) });
  const controls = useQuery({ queryKey: ["controls", systemId], queryFn: () => api.systems.controls(systemId) });
  const refresh = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ["release-gate", systemId] }),
      qc.invalidateQueries({ queryKey: ["controls", systemId] }),
      qc.invalidateQueries({ queryKey: ["conformity", systemId] }),
      qc.invalidateQueries({ queryKey: ["systems"] }),
    ]);

  return (
    <div className="space-y-6">
      <section aria-labelledby="gate-heading" className="rounded-xl border border-border bg-muted/30 p-4 space-y-2">
        <div className="flex items-center justify-between gap-3">
          <h4 id="gate-heading" className="text-xs font-semibold uppercase tracking-widest">Release gate now</h4>
          {gate.data && <DecisionBadge decision={normaliseDecision(gate.data.decision)} />}
        </div>
        {gate.isError && <p className="text-xs text-destructive">Could not load the release gate.</p>}
        {gate.data && gate.data.blockers.length > 0 && (
          <ul className="list-disc pl-5 text-[11px] text-red-700 dark:text-red-400 space-y-0.5">
            {gate.data.blockers.map((b) => <li key={b}>{b}</li>)}
          </ul>
        )}
        {gate.data && (gate.data.reviewReasons ?? []).length > 0 && (
          <>
            <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">Still needed for PASS</p>
            <ul className="list-disc pl-5 text-[11px] text-muted-foreground space-y-0.5">
              {(gate.data.reviewReasons ?? []).map((r) => <li key={r}>{r}</li>)}
            </ul>
          </>
        )}
        {gate.data && gate.data.blockers.length === 0 && (gate.data.reviewReasons ?? []).length === 0 && (
          <p className="text-[11px] text-muted-foreground">Nothing outstanding.</p>
        )}
      </section>

      <section aria-labelledby="controls-heading" className="space-y-2">
        <h4 id="controls-heading" className="text-xs font-semibold uppercase tracking-widest">Controls</h4>
        <p className="text-[10px] text-muted-foreground">
          Only compliance or an admin can mark a control PASS or lift a BLOCKED control. Engineering can raise one to
          REVIEW or BLOCKED.
        </p>
        {controls.isError && <p className="text-xs text-destructive">Could not load controls.</p>}
        <div className="divide-y divide-border rounded-xl border border-border bg-card">
          {(controls.data ?? []).map((control) => (
            <ControlRow key={control.id} systemId={systemId} control={control} role={role} onSaved={refresh} />
          ))}
        </div>
      </section>

      {riskClass === "high" && <ConformityEditor systemId={systemId} role={role} onSaved={refresh} />}
    </div>
  );
}

function ControlRow({ systemId, control, role, onSaved }: {
  systemId: string; control: SystemControl; role: string | undefined; onSaved: () => Promise<unknown>;
}) {
  const options = allowedControlStatuses(role, control.status);
  const [status, setStatus] = useState<ControlStatus>(control.status);
  const [notes, setNotes] = useState(control.notes ?? "");
  const save = useMutation({
    mutationFn: () => api.systems.updateControl(systemId, control.controlId, status, notes.trim() || undefined),
    onSuccess: onSaved,
  });
  const editable = options.length > 0;
  const statusId = `control-status-${control.id}`;
  const notesId = `control-notes-${control.id}`;

  return (
    <div className="p-3 space-y-1.5">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[11px] font-semibold">{control.controlName} <span className="font-mono text-muted-foreground">({control.controlCode})</span></p>
        <span className={cn(
          "text-[9px] font-bold uppercase px-1.5 rounded-full",
          control.status === "PASS" && "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-400",
          control.status === "REVIEW" && "bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-400",
          control.status === "BLOCKED" && "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400",
        )}>{control.status}</span>
      </div>
      {control.notes && <p className="text-[10px] text-muted-foreground italic">“{control.notes}”</p>}
      {editable && (
        <div className="grid grid-cols-[7rem_1fr_auto] items-end gap-2">
          <div>
            <label htmlFor={statusId} className="sr-only">Status for {control.controlName}</label>
            <select id={statusId} className={FIELD} value={status} onChange={(e) => setStatus(e.target.value as ControlStatus)}>
              {!options.includes(control.status) && <option value={control.status} disabled>{control.status}</option>}
              {options.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor={notesId} className="sr-only">Notes for {control.controlName}</label>
            <input id={notesId} className={FIELD} value={notes} onChange={(e) => setNotes(e.target.value)}
              placeholder="Evidence or reason" />
          </div>
          <Button size="sm" variant="outline" className="h-7 text-[10px]"
            disabled={save.isPending || (status === control.status && notes === (control.notes ?? ""))}
            onClick={() => save.mutate()}>
            Save
          </Button>
        </div>
      )}
      {save.isError && (
        <p role="alert" className="text-[10px] text-destructive">
          {save.error instanceof ApiError ? save.error.message : "Could not save the control."}
        </p>
      )}
    </div>
  );
}

function ConformityEditor({ systemId, role, onSaved }: {
  systemId: string; role: string | undefined; onSaved: () => Promise<unknown>;
}) {
  const dossier = useQuery({ queryKey: ["conformity", systemId], queryFn: () => api.systems.conformity(systemId) });
  const [draft, setDraft] = useState<ConformityDossier | null>(null);
  useEffect(() => {
    if (dossier.data) setDraft(structuredClone(dossier.data));
  }, [dossier.data]);
  const save = useMutation({
    mutationFn: () => api.systems.updateConformity(systemId, { annexIv: draft!.annexIv, fria: draft!.fria }),
    onSuccess: onSaved,
  });
  const editable = canEditConformity(role);

  function setSection(index: number, patch: Partial<ConformityDossier["annexIv"]["sections"][number]>) {
    setDraft((d) => {
      if (!d) return d;
      const sections = d.annexIv.sections.map((s, i) => (i === index ? { ...s, ...patch } : s));
      return { ...d, annexIv: { ...d.annexIv, sections } };
    });
  }

  return (
    <section aria-labelledby="conformity-heading" className="space-y-2">
      <h4 id="conformity-heading" className="text-xs font-semibold uppercase tracking-widest">Annex IV dossier and FRIA</h4>
      <p className="text-[10px] text-muted-foreground">
        A high-risk system reaches PASS only when no Annex IV section is missing and the FRIA is recorded or waived.
        This is a structured checklist, not a technical file or a conformity assessment.
      </p>
      {dossier.isError && <p className="text-xs text-destructive">Could not load the dossier.</p>}
      {draft && (
        <div className="space-y-2">
          {draft.annexIv.sections.map((section, i) => (
            <div key={section.id} className="grid grid-cols-[1fr_7rem] gap-2 rounded-lg border border-border p-2">
              <div className="space-y-1">
                <p className="text-[11px] font-semibold">{section.id} {section.title}</p>
                <label htmlFor={`annex-ref-${section.id}`} className="sr-only">Evidence for {section.id}</label>
                <input id={`annex-ref-${section.id}`} className={FIELD} disabled={!editable} value={section.evidenceRef ?? ""}
                  placeholder="Where the evidence is" onChange={(e) => setSection(i, { evidenceRef: e.target.value })} />
              </div>
              <div>
                <label htmlFor={`annex-status-${section.id}`} className="sr-only">Status for {section.id}</label>
                <select id={`annex-status-${section.id}`} className={FIELD} disabled={!editable} value={section.status}
                  onChange={(e) => setSection(i, { status: e.target.value })}>
                  {["MISSING", "PARTIAL", "PRESENT"].map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
              </div>
            </div>
          ))}
          <div className="grid grid-cols-[1fr_9rem] items-center gap-2 rounded-lg border border-border p-2">
            <label htmlFor="fria-status" className="text-[11px] font-semibold">Fundamental rights impact assessment (Art. 27)</label>
            <select id="fria-status" className={FIELD} disabled={!editable} value={draft.fria.status}
              onChange={(e) => setDraft((d) => (d ? { ...d, fria: { ...d.fria, status: e.target.value } } : d))}>
              {Array.from(new Set(["NOT_STARTED", "RECORDED", "WAIVED", draft.fria.status])).map((o) => (
                <option key={o} value={o}>{o}</option>
              ))}
            </select>
          </div>
          {editable ? (
            <Button size="sm" onClick={() => save.mutate()} disabled={save.isPending}>Save dossier</Button>
          ) : (
            <p className="text-[10px] text-muted-foreground">Compliance, legal or an admin can edit this record.</p>
          )}
          {save.isError && (
            <p role="alert" className="text-[10px] text-destructive">
              {save.error instanceof ApiError ? save.error.message : "Could not save the dossier."}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
