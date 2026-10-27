"use client";

import { useEffect, useMemo, useState } from "react";
import { Modal } from "./ui/modal";
import { Button } from "./ui/button";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";
import type {
  DeterminationObligationItem,
  DeterminationQuestion,
  DeterminationQuestionnaire,
  DeterminationRun,
  ObligationApplicability,
} from "@/lib/types";
import {
  AlertTriangle,
  CheckCircle2,
  CircleDashed,
  Loader2,
  Scale,
  ShieldAlert,
} from "lucide-react";

const FALLBACK_DISCLAIMER =
  "Assisted obligation determination (ruleset v1). This is a suggested applicability / obligation map based on questionnaire answers and the control catalog. It is not legal advice, not a legal determination, not certification, and not an official conformity assessment under the EU AI Act. A qualified human legal reviewer must confirm obligations before release decisions.";

const FALLBACK_QUESTIONNAIRE: DeterminationQuestionnaire = {
  rulesetVersion: "v1",
  disclaimer: FALLBACK_DISCLAIMER,
  productLabel: "Assisted obligation determination (ruleset v1)",
  questions: [
    {
      id: "sector",
      label: "Sector / domain",
      help: "Primary business domain of the AI system.",
      type: "select",
      required: true,
      options: [
        { value: "insurance", label: "insurance" },
        { value: "hr", label: "hr" },
        { value: "finance", label: "finance" },
        { value: "other", label: "other" },
      ],
    },
    {
      id: "decision_impact",
      label: "Decision impact",
      help: "How system outputs affect people or service access.",
      type: "select",
      required: true,
      options: [
        { value: "informational", label: "informational" },
        { value: "eligibility", label: "eligibility" },
        { value: "access_to_service", label: "access to service" },
        { value: "unknown", label: "unknown" },
      ],
    },
    {
      id: "essential_private_service",
      label: "Essential private service",
      help: "Affects access to essential private services?",
      type: "boolean_unknown",
      required: true,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
        { value: "unknown", label: "Unknown" },
      ],
    },
    {
      id: "biometric",
      label: "Biometric identification",
      help: "Used for biometric identification?",
      type: "boolean_unknown",
      required: true,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
        { value: "unknown", label: "Unknown" },
      ],
    },
    {
      id: "employment",
      label: "Employment / HR use",
      help: "Used for recruitment or worker management?",
      type: "boolean_unknown",
      required: true,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
        { value: "unknown", label: "Unknown" },
      ],
    },
    {
      id: "interacts_with_natural_persons",
      label: "Interacts with natural persons",
      help: "Direct interaction with people?",
      type: "boolean",
      required: true,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
      ],
    },
    {
      id: "profiling",
      label: "Profiling / automated scoring",
      help: "Profiles or scores natural persons?",
      type: "boolean_unknown",
      required: true,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
        { value: "unknown", label: "Unknown" },
      ],
    },
    {
      id: "human_in_loop",
      label: "Human in the loop",
      help: "Meaningful human review before outcomes?",
      type: "boolean_unknown",
      required: true,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
        { value: "unknown", label: "Unknown" },
      ],
    },
    {
      id: "users_affected",
      label: "Users affected",
      help: "Scale of natural persons potentially affected.",
      type: "select",
      required: true,
      options: [
        { value: "few", label: "few" },
        { value: "many", label: "many" },
        { value: "vulnerable", label: "vulnerable" },
      ],
    },
    {
      id: "high_risk_self_assessment",
      label: "Operator high-risk self-assessment",
      help: "Does the operator currently treat this as high-risk?",
      type: "boolean_unknown",
      required: false,
      options: [
        { value: "true", label: "Yes" },
        { value: "false", label: "No" },
        { value: "unknown", label: "Unknown" },
      ],
    },
  ],
};

interface ObligationMapWizardProps {
  isOpen: boolean;
  onClose: () => void;
  systemId: string;
  systemName: string;
}

function coerceAnswer(raw: string, type: string): string | boolean {
  if (type === "boolean" || type === "boolean_unknown") {
    if (raw === "true") return true;
    if (raw === "false") return false;
    return raw;
  }
  return raw;
}

function groupByApplicability(items: DeterminationObligationItem[]) {
  const applicable: DeterminationObligationItem[] = [];
  const uncertain: DeterminationObligationItem[] = [];
  const notApplicable: DeterminationObligationItem[] = [];
  for (const item of items) {
    if (item.applicability === "APPLICABLE") applicable.push(item);
    else if (item.applicability === "UNCERTAIN") uncertain.push(item);
    else notApplicable.push(item);
  }
  return { applicable, uncertain, notApplicable };
}

function ApplicabilityIcon({ status }: { status: ObligationApplicability }) {
  if (status === "APPLICABLE") return <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />;
  if (status === "UNCERTAIN") return <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0" />;
  return <CircleDashed className="w-4 h-4 text-muted-foreground shrink-0" />;
}

function ObligationList({
  title,
  items,
  empty,
}: {
  title: string;
  items: DeterminationObligationItem[];
  empty: string;
}) {
  return (
    <div>
      <h4 className="text-xs font-semibold uppercase tracking-widest text-foreground mb-2">
        {title}{" "}
        <span className="text-muted-foreground font-medium normal-case tracking-normal">
          ({items.length})
        </span>
      </h4>
      {items.length === 0 ? (
        <p className="text-xs text-muted-foreground italic">{empty}</p>
      ) : (
        <div className="divide-y divide-border border border-border rounded-xl overflow-hidden bg-card">
          {items.map((item) => (
            <div key={item.ruleCode} className="p-3 flex items-start gap-3">
              <ApplicabilityIcon status={item.applicability} />
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-[11px] font-semibold text-foreground leading-snug">
                    {item.title}
                  </p>
                  <span className="text-[9px] uppercase font-bold text-muted-foreground shrink-0">
                    {item.severity}
                  </span>
                </div>
                <p className="text-[10px] text-muted-foreground mt-0.5">{item.legalRefs}</p>
                <p className="text-[10px] text-muted-foreground mt-1 leading-snug">{item.rationale}</p>
                {item.controlCodes?.length > 0 && (
                  <p className="text-[10px] mt-1.5 text-foreground/80">
                    Controls:{" "}
                    <span className="font-mono text-[9px]">{item.controlCodes.join(", ")}</span>
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function ObligationMapWizard({
  isOpen,
  onClose,
  systemId,
  systemName,
}: ObligationMapWizardProps) {
  const [step, setStep] = useState<"intro" | "questions" | "results">("intro");
  const [questionnaire, setQuestionnaire] =
    useState<DeterminationQuestionnaire>(FALLBACK_QUESTIONNAIRE);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [run, setRun] = useState<DeterminationRun | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setStep("intro");
    setRun(null);
    setError(null);
    setAnswers({});
    setLoading(true);
    api.determination
      .questionnaire()
      .then((q) => {
        setQuestionnaire(q);
        const defaults: Record<string, string> = {};
        for (const question of q.questions) {
          if (question.options?.[0]) defaults[question.id] = question.options[0].value;
        }
        setAnswers(defaults);
      })
      .catch(() => {
        setQuestionnaire(FALLBACK_QUESTIONNAIRE);
        const defaults: Record<string, string> = {};
        for (const question of FALLBACK_QUESTIONNAIRE.questions) {
          if (question.options?.[0]) defaults[question.id] = question.options[0].value;
        }
        setAnswers(defaults);
      })
      .finally(() => setLoading(false));
  }, [isOpen, systemId]);

  const grouped = useMemo(
    () => (run ? groupByApplicability(run.obligations ?? []) : null),
    [run]
  );

  async function handleEvaluate() {
    setSubmitting(true);
    setError(null);
    try {
      const payload: Record<string, unknown> = {};
      for (const question of questionnaire.questions) {
        const raw = answers[question.id];
        if (raw === undefined || raw === "") {
          if (question.required) {
            throw new Error(`Please answer: ${question.label}`);
          }
          continue;
        }
        payload[question.id] = coerceAnswer(raw, question.type);
      }
      const result = await api.determination.createRun(systemId, payload);
      setRun(result);
      setStep("results");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Determination run failed");
    } finally {
      setSubmitting(false);
    }
  }

  function renderQuestion(question: DeterminationQuestion) {
    return (
      <div key={question.id} className="space-y-1.5">
        <label className="text-xs font-semibold text-foreground block">
          {question.label}
          {question.required && <span className="text-amber-700 ml-1">*</span>}
        </label>
        <p className="text-[10px] text-muted-foreground leading-snug">{question.help}</p>
        <div className="flex flex-wrap gap-1.5">
          {question.options.map((opt) => {
            const selected = answers[question.id] === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => setAnswers((p) => ({ ...p, [question.id]: opt.value }))}
                className={cn(
                  "text-[11px] px-2.5 py-1.5 rounded-lg border transition-colors",
                  selected
                    ? "border-primary bg-primary/10 text-foreground font-semibold"
                    : "border-border bg-muted/20 text-muted-foreground hover:bg-muted/40"
                )}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Suggested applicability / obligation map"
      description={`${systemName} · Assisted determination (not legal advice)`}
    >
      <div className="space-y-4 max-h-[70vh] overflow-y-auto pr-1">
        <div className="rounded-xl border border-amber-200 dark:border-amber-900/50 bg-amber-50/60 dark:bg-amber-950/20 p-3 flex gap-2.5">
          <Scale className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
          <div>
            <p className="text-[11px] font-semibold text-foreground mb-1">
              Human legal review required
            </p>
            <p className="text-[10px] text-muted-foreground leading-relaxed">
              {questionnaire.disclaimer || FALLBACK_DISCLAIMER}
            </p>
          </div>
        </div>

        {step === "intro" && (
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground leading-relaxed">
              Answer a short questionnaire. The engine maps{" "}
              <strong className="text-foreground font-semibold">suggested</strong> obligations
              to control codes using ruleset{" "}
              <span className="font-mono text-[11px]">{questionnaire.rulesetVersion}</span>.
              Risk class is never changed automatically.
            </p>
            <ul className="text-[11px] text-muted-foreground space-y-1 list-disc pl-4">
              <li>Allowed: suggested applicability / obligation map</li>
              <li>Never claimed: compliance, certification, official conformity</li>
            </ul>
            <div className="flex justify-end gap-2 pt-1">
              <Button variant="outline" size="sm" onClick={onClose}>
                Cancel
              </Button>
              <Button size="sm" disabled={loading} onClick={() => setStep("questions")}>
                {loading ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />
                    Loading
                  </>
                ) : (
                  "Start questionnaire"
                )}
              </Button>
            </div>
          </div>
        )}

        {step === "questions" && (
          <div className="space-y-4">
            <div className="space-y-4">{questionnaire.questions.map(renderQuestion)}</div>
            {error && (
              <p className="text-xs text-red-700 dark:text-red-400 flex items-center gap-1.5">
                <ShieldAlert className="w-3.5 h-3.5" />
                {error}
              </p>
            )}
            <div className="flex justify-between gap-2 pt-1">
              <Button variant="outline" size="sm" onClick={() => setStep("intro")}>
                Back
              </Button>
              <Button size="sm" disabled={submitting} onClick={handleEvaluate}>
                {submitting ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />
                    Evaluating
                  </>
                ) : (
                  "Run assisted determination"
                )}
              </Button>
            </div>
          </div>
        )}

        {step === "results" && run && grouped && (
          <div className="space-y-4">
            <div className="rounded-xl border border-border bg-muted/20 p-3 space-y-1.5">
              <p className="text-[10px] uppercase font-semibold tracking-wider text-muted-foreground">
                {run.result?.productLabel || questionnaire.productLabel}
              </p>
              <p className="text-xs text-foreground">
                Suggested risk class:{" "}
                <span className="font-semibold">
                  {run.result?.riskSuggestion?.suggestedRiskClass ?? "—"}
                </span>
                {" · "}
                <span className="text-muted-foreground">
                  not auto-applied
                  {run.result?.riskSuggestion?.requiresHumanConfirm ? " · human confirm required" : ""}
                </span>
              </p>
              {run.result?.riskSuggestion?.rationale && (
                <p className="text-[10px] text-muted-foreground leading-snug">
                  {run.result.riskSuggestion.rationale}
                </p>
              )}
            </div>

            <ObligationList
              title="Applicable"
              items={grouped.applicable}
              empty="No obligations suggested as applicable."
            />
            <ObligationList
              title="Uncertain"
              items={grouped.uncertain}
              empty="No uncertain obligations."
            />
            <ObligationList
              title="Not applicable"
              items={grouped.notApplicable}
              empty="No not-applicable rows."
            />

            <p className="text-[10px] text-muted-foreground leading-relaxed border-t border-border pt-3">
              {run.disclaimer}
            </p>

            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={() => setStep("questions")}>
                Re-run
              </Button>
              <Button size="sm" onClick={onClose}>
                Close
              </Button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
