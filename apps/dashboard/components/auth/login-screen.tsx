"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  Loader2,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { AuthCard } from "@/components/auth/auth-card";
import { OAuthButtons } from "@/components/auth/oauth-buttons";
import { safeNextPath } from "@/lib/auth-redirect";
import { cn } from "@/lib/utils";

const AUTH_ERROR_MESSAGES: Record<string, string> = {
  not_provisioned:
    "No account exists for this identity. Create a workspace, or ask your administrator for an invite.",
  denied: "Sign-in was cancelled or denied by the identity provider.",
  state: "Sign-in session expired or was invalid. Please try again.",
  unsupported_provider: "That identity provider is not supported.",
  sign_in_failed: "Sign-in failed. Please try again or use email and password.",
  sign_in_unavailable:
    "Social sign-in is temporarily unavailable. Use email and password, or try again later.",
  not_configured:
    "Social sign-in is not configured in this environment. Use email and password.",
  workspace_deleted:
    "This workspace has been deleted. Contact support within 30 days if this was a mistake.",
  email_unverified:
    "Your identity provider did not confirm this email address. Sign in with your password, then link Google or Microsoft from Settings.",
};

const inputClassName = cn(
  "w-full rounded-lg border border-border bg-background px-3 py-2.5 text-sm",
  "placeholder:text-muted-foreground",
  "outline-none transition-colors",
  "focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

export function LoginScreen({
  nextPath,
  authErrorCode,
  passwordReset = false,
}: {
  nextPath?: string;
  authErrorCode?: string;
  passwordReset?: boolean;
}) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [resendNote, setResendNote] = useState<string | null>(null);
  const [demoStarting, setDemoStarting] = useState(false);

  const oauthError = authErrorCode
    ? (AUTH_ERROR_MESSAGES[authErrorCode] ?? "Sign-in failed. Please try again.")
    : null;

  const displayError = error ?? oauthError;
  const busy = submitting || demoStarting;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setNeedsVerification(false);
    setResendNote(null);

    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    setSubmitting(false);
    if (!response.ok) {
      if (response.status === 503) {
        setError("Devernance is temporarily unavailable. Try again shortly.");
        return;
      }
      if (response.status === 403) {
        const body = await response.json().catch(() => null);
        if (body?.error === "workspace_deleted") {
          setError("This workspace has been deleted. Contact support within 30 days if this was a mistake.");
          return;
        }
        setNeedsVerification(true);
        return;
      }
      if (response.status === 429) {
        const body = await response.json().catch(() => null);
        const message =
          body && typeof body.error === "string"
            ? body.error
            : "Too many sign-in attempts. Try again in 15 minutes.";
        setError(message);
        return;
      }
      setError("Invalid email or password");
      return;
    }
    router.push(safeNextPath(nextPath));
  }

  async function resendVerification() {
    setResendNote(null);
    const res = await fetch("/api/auth/verify-email?resend=1", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }),
    });
    setResendNote(
      res.status === 202
        ? "If that address is waiting for confirmation, we sent a new link."
        : "Could not resend right now. Try again shortly.",
    );
  }

  async function startDemo() {
    setError(null);
    setDemoStarting(true);
    const res = await fetch("/api/auth/demo", { method: "POST" });
    if (res.ok) {
      router.push("/command");
      return;
    }
    setDemoStarting(false);
    setError(
      res.status === 503
        ? "Devernance is temporarily unavailable. Try again shortly."
        : "The live demo is not available right now.",
    );
  }

  return (
    <AuthCard
      variant="signin"
      title="Sign in to your workspace"
      description="Use your work email, or continue with Google or Microsoft."
    >
            {passwordReset && !displayError && !needsVerification ? (
              <div
                role="status"
                className="mb-6 flex gap-3 rounded-lg border border-primary/30 bg-primary/10 px-3 py-3 text-sm"
              >
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
                <p>Password updated — sign in with your new password.</p>
              </div>
            ) : null}

            {needsVerification ? (
              <div
                role="alert"
                className="mb-6 flex gap-3 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-3 text-sm text-destructive"
              >
                <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                <div>
                  <p>
                    Confirm your email first.{" "}
                    <button
                      type="button"
                      onClick={resendVerification}
                      className="font-medium underline underline-offset-4"
                    >
                      Resend link
                    </button>
                  </p>
                  {resendNote ? <p className="mt-1">{resendNote}</p> : null}
                </div>
              </div>
            ) : null}

            {displayError ? (
              <div
                role="alert"
                className="mb-6 flex gap-3 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-3 text-sm text-destructive"
              >
                <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                <p>{displayError}</p>
              </div>
            ) : null}

            <OAuthButtons next={nextPath} disabled={busy} onStart={() => setError(null)} />

            <div className="relative my-8">
              <div className="absolute inset-0 flex items-center" aria-hidden="true">
                <span className="w-full border-t border-border" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-white px-3 font-medium uppercase tracking-wider text-muted-foreground">
                  or email
                </span>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label htmlFor="email" className="text-sm font-medium">
                  Work email
                </label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  disabled={busy}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@company.com"
                  className={inputClassName}
                />
              </div>
              <div className="space-y-1.5">
                <div className="flex items-baseline justify-between">
                  <label htmlFor="password" className="text-sm font-medium">
                    Password
                  </label>
                  <Link
                    href="/forgot-password"
                    className="text-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                  >
                    Forgot password?
                  </Link>
                </div>
                <input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  disabled={busy}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className={inputClassName}
                />
              </div>
              <Button
                type="submit"
                size="lg"
                disabled={busy}
                className="h-11 w-full gap-2"
              >
                {submitting ? (
                  <>
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                    Signing in…
                  </>
                ) : (
                  <>
                    Sign in
                    <ArrowRight className="size-4" aria-hidden="true" />
                  </>
                )}
              </Button>
            </form>

            <p className="mt-3 text-center text-sm text-muted-foreground">
              Just looking?{" "}
              <button
                type="button"
                disabled={busy}
                onClick={startDemo}
                className="font-medium text-foreground underline-offset-4 hover:underline disabled:opacity-50"
              >
                {demoStarting ? "Opening the demo…" : "Try the live demo"}
              </button>
            </p>

            <p className="mt-6 text-center text-xs text-muted-foreground">
              By signing in you agree to our{" "}
              <Link href="/terms" className="underline-offset-4 hover:underline">
                Terms
              </Link>{" "}
              and{" "}
              <Link href="/privacy" className="underline-offset-4 hover:underline">
                Privacy Policy
              </Link>
              .
            </p>
    </AuthCard>
  );
}
