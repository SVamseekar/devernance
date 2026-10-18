import Link from "next/link";
import type { ReactNode } from "react";
import { CheckCircle2 } from "lucide-react";

import { DevernanceMark } from "@/components/marketing/brand-icons";
import { PLANS } from "@/lib/pricing";
import { siteConfig } from "@/lib/site-config";
import { cn } from "@/lib/utils";

export const authInputClassName = cn(
  "w-full rounded-lg border border-border bg-background px-3 py-2.5 text-sm",
  "placeholder:text-muted-foreground",
  "outline-none transition-colors",
  "focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

export type AuthVariant = "signin" | "signup" | "account";

const FREE = PLANS.find((p) => p.code === "FREE");

const panel: Record<AuthVariant, { eyebrow: string; heading: string; body: string; points: string[] }> = {
  signin: {
    eyebrow: "Sign in",
    heading: "Welcome back.",
    body: "Pick up where your last release left off.",
    points: [
      "Release decisions and their blockers",
      "Evidence, evaluations and data contracts",
      "Approvals and the audit ledger",
      "API keys for your CI pipeline",
    ],
  },
  signup: {
    eyebrow: "Start free",
    heading: "Gate your first AI system today.",
    body: "Your workspace starts with a 14-day trial. No card required.",
    points: [
      `${FREE?.gatedSystems ?? 1} gated AI system on the free plan`,
      `${FREE?.editors ?? 3} editors, unlimited viewers`,
      `${FREE?.gateRunsPerMonth ?? 300} gate runs a month`,
      "Open-source CLI to check evidence locally",
    ],
  },
  account: {
    eyebrow: "Account",
    heading: "Release gates and signed evidence packs for AI.",
    body: "Evidence and readiness tooling. Not legal advice.",
    points: [],
  },
};

const signupSteps = ["Create your workspace", "Confirm your email and set a password", "Register a system and gate it in CI"];

/** Split layout shared by every auth page: navy brand panel (what this page is for) and a white form panel. */
export function AuthCard({
  title,
  description,
  children,
  footer,
  variant = "account",
}: {
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  variant?: AuthVariant;
}) {
  const copy = panel[variant];
  const switchLink =
    variant === "signup"
      ? { prompt: "Have an account?", href: "/login", label: "Sign in" }
      : variant === "signin"
        ? { prompt: "New here?", href: "/signup", label: "Start free" }
        : { prompt: "", href: "/login", label: "Sign in" };

  return (
    <div className="mk-light flex min-h-screen bg-white font-sans text-ink [&_.font-heading]:font-sans [&_.font-heading]:font-bold">
      <aside className="mk-night relative hidden flex-col justify-between p-12 text-white lg:flex lg:w-[44%] xl:px-16">
        <Link href="/" className="inline-flex items-center gap-2.5 text-[17px] font-bold tracking-tight">
          <DevernanceMark className="size-8" />
          {siteConfig.shortName}
        </Link>
        <div>
          <p className="inline-block rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-[#c9d1ff]">
            {copy.eyebrow}
          </p>
          <p className="mt-5 max-w-md text-4xl font-bold tracking-tight text-balance">{copy.heading}</p>
          <p className="mt-4 max-w-md text-on-dark-muted">{copy.body}</p>
          {copy.points.length ? (
            <ul className="mt-8 space-y-3">
              {copy.points.map((point) => (
                <li key={point} className="flex items-start gap-3 text-sm text-white/90">
                  <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-[#4ade80]" aria-hidden="true" />
                  {point}
                </li>
              ))}
            </ul>
          ) : null}
          {variant === "signup" ? (
            <ol className="mt-10 space-y-3 border-t border-white/10 pt-8">
              {signupSteps.map((step, i) => (
                <li key={step} className="flex items-center gap-3 text-sm text-white/90">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-periwinkle text-xs font-bold text-navy-950">
                    {i + 1}
                  </span>
                  {step}
                </li>
              ))}
            </ol>
          ) : null}
        </div>
        <p className="text-xs text-on-dark-muted">
          Governance software for evidence and release readiness. Not legal advice.
        </p>
      </aside>

      <div className="flex flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-line px-4 sm:px-6 lg:border-0 lg:px-10">
          <Link href="/" className="inline-flex items-center gap-2 text-[15px] font-bold lg:invisible">
            <DevernanceMark className="size-7" />
            {siteConfig.shortName}
          </Link>
          <p className="text-sm text-ink-muted">
            {switchLink.prompt}{" "}
            <Link
              href={switchLink.href}
              className="ml-1 inline-flex h-9 items-center rounded-lg border border-line px-3 font-semibold text-ink hover:bg-mist"
            >
              {switchLink.label}
            </Link>
          </p>
        </header>
        <main className="flex flex-1 items-center justify-center px-4 py-10 sm:px-6">
          <div className="w-full max-w-[400px]">
            <h1 className="text-3xl font-bold tracking-tight text-ink">{title}</h1>
            {description ? <p className="mt-2 text-sm text-ink-muted">{description}</p> : null}
            <div className="mt-8">{children}</div>
            {footer ? <div className="mt-8 text-center text-sm text-ink-muted">{footer}</div> : null}
          </div>
        </main>
        <footer className="flex justify-center gap-4 border-t border-line px-4 py-4 text-xs text-ink-muted">
          <Link href="/" className="hover:text-ink">
            ← Back to {siteConfig.shortName}
          </Link>
          <a href={`mailto:${siteConfig.supportEmail}`} className="hover:text-ink">
            Support
          </a>
        </footer>
      </div>
    </div>
  );
}

export function FormError({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
      {message}
    </p>
  );
}
