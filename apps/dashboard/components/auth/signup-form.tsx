"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Loader2, MailCheck } from "lucide-react";

import { AuthCard, FormError, authInputClassName } from "@/components/auth/auth-card";
import { OAuthButtons } from "@/components/auth/oauth-buttons";
import { TurnstileWidget, turnstileEnabled } from "@/components/auth/turnstile-widget";
import { Button } from "@/components/ui/button";
import { savePlanIntent, type PlanIntent } from "@/lib/plan-intent";

export function SignupForm({ planIntent = null }: { planIntent?: PlanIntent | null }) {
  // Remember the plan picked on /pricing; onboarding offers its checkout after the first system is gated.
  useEffect(() => {
    if (planIntent) savePlanIntent(planIntent);
  }, [planIntent]);
  const [email, setEmail] = useState("");
  const [organisationName, setOrganisationName] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState("");
  const [challengeKey, setChallengeKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [resendNote, setResendNote] = useState<string | null>(null);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch("/api/auth/signup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, organisationName, turnstileToken }),
    });
    setBusy(false);
    setTurnstileToken("");
    setChallengeKey((k) => k + 1);
    if (res.status === 202) {
      setSent(true);
      return;
    }
    const body = await res.json().catch(() => null);
    setError(
      res.status === 503
        ? "Devernance is temporarily unavailable. Try again shortly."
        : typeof body?.error === "string"
          ? body.error
          : "Could not create the workspace. Please try again.",
    );
  }

  async function resend() {
    setResendNote(null);
    const res = await fetch("/api/auth/verify-email?resend=1", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }),
    });
    setResendNote(
      res.status === 202
        ? "Sent again. It can take a minute to arrive; check spam too."
        : "Could not resend right now. Try again shortly.",
    );
  }

  if (sent) {
    return (
      <AuthCard
        variant="signup"
        title="Check your inbox"
        footer={
          <>
            Wrong address?{" "}
            <button type="button" className="font-medium text-foreground underline-offset-4 hover:underline" onClick={() => setSent(false)}>
              Start again
            </button>
          </>
        }
      >
        <div className="space-y-4 text-sm">
          <p className="flex items-start gap-2">
            <MailCheck className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
            <span>
              We sent a confirmation link to <strong>{email}</strong>. Open it to choose your password and
              finish creating your workspace. The link expires in 24 hours.
            </span>
          </p>
          <p className="text-muted-foreground">
            If this address already has an account, we sent a note about that instead.
          </p>
          <Button type="button" variant="outline" onClick={resend}>
            Resend the email
          </Button>
          {resendNote ? <p className="text-muted-foreground">{resendNote}</p> : null}
        </div>
      </AuthCard>
    );
  }

  const canSubmit = agreed && !busy && (!turnstileEnabled || turnstileToken !== "");

  return (
    <AuthCard
      variant="signup"
      title="Create your free workspace"
      description="Takes about two minutes. You choose your password from the link we email you."
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <FormError message={error} />
        <div className="space-y-1.5">
          <label htmlFor="email" className="text-sm font-medium">
            Work email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            maxLength={254}
            disabled={busy}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@company.com"
            className={authInputClassName}
          />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="organisation" className="text-sm font-medium">
            Organisation name
          </label>
          <input
            id="organisation"
            type="text"
            autoComplete="organization"
            required
            maxLength={120}
            disabled={busy}
            value={organisationName}
            onChange={(e) => setOrganisationName(e.target.value)}
            className={authInputClassName}
          />
        </div>
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            required
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            className="mt-1"
          />
          <span>
            I agree to the{" "}
            <Link href="/terms" className="underline-offset-4 hover:underline">
              Terms
            </Link>{" "}
            and{" "}
            <Link href="/privacy" className="underline-offset-4 hover:underline">
              Privacy Policy
            </Link>
            .
          </span>
        </label>
        <TurnstileWidget key={challengeKey} onToken={setTurnstileToken} />
        <Button type="submit" size="lg" disabled={!canSubmit} className="h-11 w-full gap-2">
          {busy ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Creating…
            </>
          ) : (
            "Create workspace"
          )}
        </Button>
      </form>
      <div className="relative my-6">
        <div className="absolute inset-0 flex items-center" aria-hidden="true">
          <span className="w-full border-t border-border" />
        </div>
        <div className="relative flex justify-center text-xs">
          <span className="bg-white px-3 font-medium uppercase tracking-wider text-muted-foreground">or sign up with</span>
        </div>
      </div>
      <OAuthButtons next="/onboarding" disabled={busy} />
    </AuthCard>
  );
}
