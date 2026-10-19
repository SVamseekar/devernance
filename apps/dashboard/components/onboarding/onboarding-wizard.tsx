"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { RiskBadge } from "@/components/risk-badge";
import { api, ApiError } from "@/lib/api";
import { useCorpus } from "@/hooks/use-corpus";
import { evidenceTypesFor, firstDutyDates, releaseGateWorkflow } from "@/lib/onboarding";
import { siteConfig } from "@/lib/site-config";
import { clearPlanIntent, loadPlanIntent, type PlanIntent } from "@/lib/plan-intent";
import { PLANS } from "@/lib/pricing";
import { registerSystem } from "@/lib/register-system";
import type { RiskAnswers } from "@/lib/risk-class";
import { SECTOR_PACK_OPTIONS } from "@/lib/sector-packs";
import { QUESTIONNAIRE, deriveRegistration, sectorAnswerDefaults } from "@/lib/system-registration";
import type { ApiKeyCreated, RiskClass } from "@/lib/types";

const inputClass = "mt-1 block w-full rounded-lg border border-border bg-background px-3 py-2 text-sm";

function SkipLink() {
  return (
    <Link href="/command" className="text-xs text-muted-foreground underline-offset-4 hover:underline">
      I&apos;ll do this later
    </Link>
  );
}

export function OnboardingWizard() {
  const [step, setStep] = useState(1);
  const [system, setSystem] = useState<{ id: string; riskClass: RiskClass } | null>(null);

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <p className="text-xs text-muted-foreground">Step {step} of 3</p>
      {step === 1 && (
        <RegisterStep
          onRegistered={(created) => {
            setSystem(created);
            setStep(2);
          }}
        />
      )}
      {step === 2 && system && <AppliesStep system={system} onNext={() => setStep(3)} />}
      {step === 3 && system && <GateStep systemId={system.id} />}
    </div>
  );
}

function RegisterStep({ onRegistered }: { onRegistered: (s: { id: string; riskClass: RiskClass }) => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [owner, setOwner] = useState("");
  const [purpose, setPurpose] = useState("");
  const [sector, setSector] = useState("other");
  const [answers, setAnswers] = useState<RiskAnswers>({
    q_biometrics: false,
    q_essential: false,
    q_hr: false,
    q_interaction: false,
  });
  const [error, setError] = useState<string | null>(null);

  const suggestion = deriveRegistration(answers, sector);
  const register = useMutation({
    mutationFn: () => registerSystem({ name, owner, purpose, sector, answers }),
    onSuccess: async (created) => {
      await qc.invalidateQueries({ queryKey: ["systems"] });
      onRegistered(created);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Could not register the system. Try again."),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Register your first AI system</CardTitle>
        <CardDescription>Four answers give a suggested risk class. You confirm it; nothing is decided for you.</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            register.mutate();
          }}
        >
          <label className="block text-xs font-medium">
            System name
            <input required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Claims triage assistant" className={inputClass} />
          </label>
          <label className="block text-xs font-medium">
            Owner
            <input required value={owner} onChange={(e) => setOwner(e.target.value)} placeholder="e.g. Claims Operations" className={inputClass} />
          </label>
          <label className="block text-xs font-medium">
            Purpose
            <textarea required rows={3} value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="What does it decide or automate, and who is affected?" className={inputClass} />
          </label>
          <label className="block text-xs font-medium">
            Sector
            <select
              value={sector}
              onChange={(e) => {
                setSector(e.target.value);
                const defaults = sectorAnswerDefaults(e.target.value);
                if (defaults) setAnswers((a) => ({ ...a, ...defaults }));
              }}
              className={inputClass}
            >
              {SECTOR_PACK_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>

          <div className="space-y-3 border-t border-border pt-4">
            {QUESTIONNAIRE.map((q) => (
              <div key={q.id} className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold">{q.label}</p>
                  <p className="text-[11px] text-muted-foreground">{q.text}</p>
                </div>
                <div className="flex shrink-0 gap-1" role="group" aria-label={q.label}>
                  {[true, false].map((value) => (
                    <button
                      key={String(value)}
                      type="button"
                      aria-pressed={answers[q.id] === value}
                      onClick={() => setAnswers((a) => ({ ...a, [q.id]: value }))}
                      className={`rounded-md border px-2.5 py-1 text-xs font-medium ${
                        answers[q.id] === value ? "border-primary bg-primary/10" : "border-border text-muted-foreground"
                      }`}
                    >
                      {value ? "Yes" : "No"}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="rounded-lg border border-border bg-muted/40 px-3 py-3 text-xs">
            <p className="flex items-center gap-2 font-medium">
              Suggested class: <RiskBadge risk={suggestion.riskClass} />
            </p>
            <p className="mt-1 text-muted-foreground">{suggestion.riskBasis}</p>
            <p className="mt-1 text-muted-foreground">Suggestion only, not legal advice.</p>
          </div>

          {error && <p role="alert" className="text-xs text-destructive">{error}</p>}
          <div className="flex items-center justify-between">
            <SkipLink />
            <Button type="submit" disabled={register.isPending || !name.trim() || !owner.trim() || !purpose.trim()}>
              {register.isPending ? "Registering…" : "Register system"}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function AppliesStep({ system, onNext }: { system: { id: string; riskClass: RiskClass }; onNext: () => void }) {
  const gate = useQuery({ queryKey: ["release-gate", system.id], queryFn: () => api.systems.releaseGate(system.id) });
  const corpus = useCorpus();
  const dates = firstDutyDates(corpus.data?.provisions ?? []);
  const needed = evidenceTypesFor(system.riskClass);

  return (
    <Card>
      <CardHeader>
        <CardTitle>See what applies</CardTitle>
        <CardDescription>Where this system stands against the release gate today.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {gate.isLoading && <p className="text-muted-foreground">Checking the release gate…</p>}
        {gate.isError && <p className="text-destructive">The release gate could not be loaded. You can still continue.</p>}
        {gate.data && (
          <div>
            <p>
              Release decision: <strong>{gate.data.decision}</strong>
            </p>
            {gate.data.blockers.length > 0 ? (
              <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
                {gate.data.blockers.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            ) : (
              <p className="mt-1 text-xs text-muted-foreground">No open blockers.</p>
            )}
          </div>
        )}

        <div className="border-t border-border pt-4">
          <p className="font-medium">Dates from the pinned corpus</p>
          <ul className="mt-1 space-y-1 text-xs text-muted-foreground">
            <li>Article 50 transparency: {dates.article50 ? `${dates.article50.date} (${dates.article50.status.replace("_", " ").toLowerCase()})` : "not in this corpus"}</li>
            <li>Annex III high-risk duties: {dates.annexIII ? `${dates.annexIII.date} (${dates.annexIII.status.replace("_", " ").toLowerCase()})` : "not in this corpus"}</li>
          </ul>
        </div>

        <div className="border-t border-border pt-4">
          <p className="font-medium">Evidence the gate looks for</p>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
            {needed.map((t) => (
              <li key={t}>{t}</li>
            ))}
          </ul>
          <Link href="/evidence" className="mt-2 inline-block text-xs font-medium underline underline-offset-4">
            Add evidence
          </Link>
        </div>

        <div className="flex items-center justify-between border-t border-border pt-4">
          <SkipLink />
          <Button onClick={onNext}>Next: gate your pipeline</Button>
        </div>
      </CardContent>
    </Card>
  );
}

function GateStep({ systemId }: { systemId: string }) {
  const router = useRouter();
  const [created, setCreated] = useState<ApiKeyCreated | null>(null);
  const [copied, setCopied] = useState(false);
  const createKey = useMutation({
    mutationFn: () => api.apiKeys.create("CI"),
    onSuccess: setCreated,
  });

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Gate your pipeline</CardTitle>
        <CardDescription>Make CI fail unless the release gate passes. You can manage keys later in Settings.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {!created ? (
          <>
            <Button onClick={() => createKey.mutate()} disabled={createKey.isPending}>
              {createKey.isPending ? "Creating…" : "Create CI key"}
            </Button>
            {createKey.isError && (
              <p className="text-xs text-destructive">Could not create a key. Admins and AI engineering leads can create keys.</p>
            )}
          </>
        ) : (
          <div className="space-y-2 rounded-lg border border-border bg-muted px-3 py-3 text-xs">
            <p className="font-medium">Copy this key now. You won&apos;t see it again.</p>
            <div className="flex gap-2">
              <input
                readOnly
                value={created.key}
                aria-label="New CI key"
                onFocus={(e) => e.currentTarget.select()}
                className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-xs"
              />
              <Button type="button" size="sm" variant="outline" onClick={() => copy(created.key)}>
                {copied ? "Copied" : "Copy"}
              </Button>
            </div>
            <p className="text-muted-foreground">Store it as the repository secret ASSURANCE_API_KEY.</p>
          </div>
        )}
        <pre className="overflow-x-auto rounded-lg bg-muted px-3 py-3 text-xs">{releaseGateWorkflow(systemId, siteConfig.appUrl)}</pre>
        <PlanUpgradeOffer />
        <div className="flex items-center justify-between border-t border-border pt-4">
          <SkipLink />
          <Button onClick={() => router.push("/command")}>Done</Button>
        </div>
      </CardContent>
    </Card>
  );
}

/** Shown when the visitor picked Team or Business on /pricing before signing up. */
function PlanUpgradeOffer() {
  const [intent, setIntent] = useState<PlanIntent | null>(null);
  useEffect(() => setIntent(loadPlanIntent()), []);
  const checkout = useMutation({
    mutationFn: (i: PlanIntent) => api.billing.checkout(i.plan, i.interval),
    onSuccess: ({ checkoutUrl }) => {
      clearPlanIntent();
      window.location.assign(checkoutUrl);
    },
  });
  if (!intent) return null;
  const plan = PLANS.find((p) => p.code === intent.plan);
  if (!plan) return null;
  return (
    <div className="rounded-lg border border-primary/30 bg-primary/5 px-3 py-3">
      <p className="font-medium">You picked {plan.name} ({intent.interval === "YEARLY" ? "yearly" : "monthly"}).</p>
      <p className="mt-1 text-xs text-muted-foreground">
        Upgrade now, or keep the free trial and upgrade later in Settings → Billing.
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button size="sm" onClick={() => checkout.mutate(intent)} disabled={checkout.isPending}>
          {checkout.isPending ? "Opening checkout…" : `Upgrade to ${plan.name}`}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => {
            clearPlanIntent();
            setIntent(null);
          }}
        >
          Not now
        </Button>
      </div>
      {checkout.isError && <p className="mt-2 text-xs text-destructive">Checkout could not start. Try again from Settings → Billing.</p>}
    </div>
  );
}
