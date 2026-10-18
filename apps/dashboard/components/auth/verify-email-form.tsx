"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Loader2 } from "lucide-react";

import { AuthCard, FormError, authInputClassName } from "@/components/auth/auth-card";
import { Button } from "@/components/ui/button";
import { passwordProblem } from "@/lib/auth-forms";

export function VerifyEmailForm() {
  const token = useSearchParams().get("token") ?? "";
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [resendEmail, setResendEmail] = useState("");
  const [resendNote, setResendNote] = useState<string | null>(null);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const problem = passwordProblem(password, confirm);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    const res = await fetch("/api/auth/verify-email", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, password }),
    });
    setBusy(false);
    if (res.ok) {
      router.replace("/onboarding");
      return;
    }
    if (res.status === 410) {
      setExpired(true);
      return;
    }
    const body = await res.json().catch(() => null);
    setError(
      res.status === 503
        ? "Devernance is temporarily unavailable. Try again shortly."
        : typeof body?.error === "string"
          ? body.error
          : "Could not confirm your email. Please try again.",
    );
  }

  async function resend(event: React.FormEvent) {
    event.preventDefault();
    const res = await fetch("/api/auth/verify-email?resend=1", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: resendEmail }),
    });
    setResendNote(
      res.status === 202
        ? "If that address is waiting for confirmation, we sent a new link."
        : "Could not resend right now. Try again shortly.",
    );
  }

  if (!token || expired) {
    return (
      <AuthCard
        title="This link can't be used"
        description="It is missing, invalid, already used, or expired. Enter your email and we will send a new one."
        footer={
          <Link href="/signup" className="font-medium text-foreground underline-offset-4 hover:underline">
            Start over
          </Link>
        }
      >
        <form onSubmit={resend} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="resend-email" className="text-sm font-medium">
              Work email
            </label>
            <input
              id="resend-email"
              type="email"
              required
              value={resendEmail}
              onChange={(e) => setResendEmail(e.target.value)}
              className={authInputClassName}
            />
          </div>
          <Button type="submit" variant="outline">
            Send a new link
          </Button>
          {resendNote ? <p className="text-sm text-muted-foreground">{resendNote}</p> : null}
        </form>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose your password" description="This finishes confirming your email and creates your workspace.">
      <form onSubmit={onSubmit} className="space-y-4">
        <FormError message={error} />
        <div className="space-y-1.5">
          <label htmlFor="password" className="text-sm font-medium">
            Password (12+ characters)
          </label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
            disabled={busy}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={authInputClassName}
          />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="confirm" className="text-sm font-medium">
            Confirm password
          </label>
          <input
            id="confirm"
            type="password"
            autoComplete="new-password"
            required
            disabled={busy}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            className={authInputClassName}
          />
        </div>
        <Button type="submit" size="lg" disabled={busy} className="h-11 w-full gap-2">
          {busy ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Confirming…
            </>
          ) : (
            "Confirm and continue"
          )}
        </Button>
      </form>
    </AuthCard>
  );
}
