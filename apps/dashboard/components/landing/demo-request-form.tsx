"use client";

import Link from "next/link";
import { useState, type ComponentProps } from "react";
import { AlertCircle, ArrowRight, CheckCircle2, Loader2 } from "lucide-react";

import { mkButton } from "@/components/marketing/primitives";
import { DEMO_ROLES, submitDemoRequest, type DemoRequestInput } from "@/lib/demo-request";
import { contactEmails, siteConfig } from "@/lib/site-config";
import { cn } from "@/lib/utils";

const EMPTY_FORM: DemoRequestInput = {
  workEmail: "",
  companyName: "",
  jobTitle: "",
  message: "",
  marketingConsent: false,
  privacyConsent: false,
  website: "",
  formStartedAt: 0,
};

const fieldClass =
  "mt-1.5 min-h-11 w-full rounded-lg border border-line bg-white px-3 py-2.5 text-sm text-ink outline-none placeholder:text-ink-muted/70 focus-visible:border-brand focus-visible:ring-3 focus-visible:ring-brand/20";
const labelClass = "block text-sm font-semibold text-ink";

function Required() {
  return (
    <span className="text-blocked" aria-hidden="true">
      {" "}
      *
    </span>
  );
}

/** Storyboard frame 12: four fields, one button. Details come up in the conversation itself. */
export function DemoRequestForm() {
  const [form, setForm] = useState<DemoRequestInput>(() => ({ ...EMPTY_FORM, formStartedAt: Date.now() }));
  const [status, setStatus] = useState<"idle" | "submitting" | "success" | "error">("idle");
  const [errorMessage, setErrorMessage] = useState("");

  const updateField = <K extends keyof DemoRequestInput>(key: K, value: DemoRequestInput[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleSubmit: NonNullable<ComponentProps<"form">["onSubmit"]> = async (event) => {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    setStatus("submitting");
    setErrorMessage("");
    try {
      await submitDemoRequest(form);
      setStatus("success");
    } catch (error) {
      setStatus("error");
      setErrorMessage(error instanceof Error ? error.message : "Something went wrong");
    }
  };

  if (status === "success") {
    return (
      <div className="rounded-2xl border border-line bg-white p-8 text-center shadow-xl shadow-navy-950/5" role="status">
        <CheckCircle2 className="mx-auto h-8 w-8 text-pass" aria-hidden="true" />
        <h2 className="mt-4 text-xl font-semibold text-ink">Request received</h2>
        <p className="mt-2 text-sm text-ink-muted">
          Thanks. We will reply to <strong className="text-ink">{form.workEmail}</strong> within one business day.
        </p>
        <Link href="/" className={cn(mkButton.outline, "mt-6")}>
          Back to home
        </Link>
      </div>
    );
  }

  return (
    <form
      className="relative space-y-5 rounded-2xl border border-line bg-white p-6 shadow-xl shadow-navy-950/5 sm:p-8"
      onSubmit={handleSubmit}
    >
      {/* Honeypot — hidden from people, filled by bots. */}
      <div className="absolute -left-[9999px] top-auto h-0 w-0 overflow-hidden" aria-hidden="true">
        <label>
          Website
          <input
            type="text"
            name="website"
            tabIndex={-1}
            autoComplete="off"
            value={form.website}
            onChange={(e) => updateField("website", e.target.value)}
          />
        </label>
      </div>

      <label className={labelClass}>
        Work email
        <Required />
        <input
          required
          type="email"
          autoComplete="email"
          placeholder="you@company.com"
          className={fieldClass}
          value={form.workEmail}
          onChange={(e) => updateField("workEmail", e.target.value)}
        />
      </label>
      <label className={labelClass}>
        Company
        <Required />
        <input
          required
          autoComplete="organization"
          placeholder="Your company"
          className={fieldClass}
          value={form.companyName}
          onChange={(e) => updateField("companyName", e.target.value)}
        />
      </label>
      <label className={labelClass}>
        Role
        <Required />
        <select
          required
          className={fieldClass}
          value={form.jobTitle}
          onChange={(e) => updateField("jobTitle", e.target.value)}
        >
          <option value="">Select a role</option>
          {DEMO_ROLES.map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </select>
      </label>
      <label className={labelClass}>
        How can we help?
        <Required />
        <textarea
          required
          rows={4}
          maxLength={4000}
          placeholder="Tell us about your use case, for example the AI systems you plan to gate."
          className={cn(fieldClass, "resize-y")}
          value={form.message}
          onChange={(e) => updateField("message", e.target.value)}
        />
      </label>

      <div className="space-y-2.5 text-sm">
        <label className="flex items-start gap-2 text-ink/85">
          <input
            type="checkbox"
            required
            className="mt-0.5 accent-brand"
            checked={form.privacyConsent}
            onChange={(e) => updateField("privacyConsent", e.target.checked)}
          />
          <span>
            I agree to the processing of my details as described in the{" "}
            <Link href="/privacy" className="font-medium text-brand hover:underline">
              Privacy Policy
            </Link>
            <Required />
          </span>
        </label>
        <label className="flex items-start gap-2 text-ink-muted">
          <input
            type="checkbox"
            className="mt-0.5 accent-brand"
            checked={form.marketingConsent}
            onChange={(e) => updateField("marketingConsent", e.target.checked)}
          />
          <span>Send me product updates (optional, unsubscribe anytime).</span>
        </label>
      </div>

      {status === "error" ? (
        <div
          className="flex items-start gap-2 rounded-lg border border-blocked/30 bg-blocked-soft px-3 py-2 text-sm text-blocked"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <button type="submit" className={cn(mkButton.primary, "h-12 w-full text-base")} disabled={status === "submitting"}>
        {status === "submitting" ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            Sending…
          </>
        ) : (
          <>
            Request a demo
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </>
        )}
      </button>
      <p className="text-center text-xs text-ink-muted">
        Prefer email?{" "}
        <a href={`mailto:${contactEmails.demo}`} className="font-medium text-brand hover:underline">
          {contactEmails.demo}
        </a>
      </p>
    </form>
  );
}
