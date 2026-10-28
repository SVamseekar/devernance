"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { billingBanner, usagePercent } from "@/lib/billing-view";
import { PLANS, formatLimit, formatPrice } from "@/lib/pricing";

function UsageBar({ label, used, limit }: { label: string; used: number; limit: number }) {
  const percent = usagePercent(used, limit);
  return (
    <div className="text-xs">
      <div className="flex justify-between">
        <span>{label}</span>
        <span>
          {used} / {limit < 0 ? "unlimited" : limit}
        </span>
      </div>
      {percent !== null && (
        <div className="mt-1 h-1.5 overflow-hidden rounded bg-muted">
          <div className={`h-full ${percent >= 100 ? "bg-destructive" : "bg-primary"}`} style={{ width: `${percent}%` }} />
        </div>
      )}
    </div>
  );
}

export function BillingCard() {
  const qc = useQueryClient();
  const billing = useQuery({ queryKey: ["billing"], queryFn: api.billing.get, retry: false });
  const [interval, setInterval_] = useState<"MONTHLY" | "YEARLY">("MONTHLY");
  const [returning, setReturning] = useState(false);
  const checkoutError = (e: unknown) => (e instanceof Error ? e.message : "Checkout could not start.");

  // After Dodo's hosted checkout the plan changes through a webhook, so poll briefly.
  useEffect(() => {
    if (typeof window === "undefined" || new URLSearchParams(window.location.search).get("billing") !== "return") return;
    setReturning(true);
    window.history.replaceState(null, "", window.location.pathname + "#billing");
    let polls = 0;
    const timer = window.setInterval(() => {
      polls += 1;
      void qc.invalidateQueries({ queryKey: ["billing"] });
      if (polls >= 10) {
        window.clearInterval(timer);
        setReturning(false);
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [qc]);

  const checkout = useMutation({
    mutationFn: (plan: "TEAM" | "BUSINESS") => api.billing.checkout(plan, interval),
    onSuccess: ({ checkoutUrl }) => window.location.assign(checkoutUrl),
  });
  const portal = useMutation({
    mutationFn: () => api.billing.portal(),
    onSuccess: ({ url }) => window.location.assign(url),
  });

  const b = billing.data;
  const banner = b ? billingBanner(b) : null;
  const paid = b?.plan === "TEAM" || b?.plan === "BUSINESS";
  // A workspace that already pays changes plan in the billing portal; a second checkout would bill twice.
  const subscribed = paid && (b?.status === "ACTIVE" || b?.status === "ON_HOLD");

  return (
    <Card id="billing">
      <CardHeader>
        <CardTitle>Plan and billing</CardTitle>
        <CardDescription>
          Dodo Payments is the merchant of record and issues your invoice. Prices exclude tax where applicable.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {returning && (
          <p role="status" className="rounded-lg bg-muted px-3 py-2 text-xs">
            {paid ? "Your plan is active." : "If you completed the payment, your plan updates in a few seconds."}
          </p>
        )}
        {billing.isError && <p className="text-xs text-destructive">Billing details could not be loaded.</p>}
        {b && (
          <>
            <p className="text-sm">
              Current plan: <strong>{b.plan}</strong>
              {b.status !== "NONE" && <span className="text-muted-foreground"> · {b.status.toLowerCase()}</span>}
              {b.interval && <span className="text-muted-foreground"> · {b.interval.toLowerCase()}</span>}
            </p>
            {banner && <p className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-xs">{banner}</p>}
            <div className="space-y-2">
              <UsageBar label="Gated systems" used={b.usage.systems} limit={b.limits.gatedSystems} />
              <UsageBar label="Editor seats" used={b.usage.editors} limit={b.limits.editorSeats} />
              <UsageBar label="CI gate runs this month" used={b.usage.gateRunsThisMonth} limit={b.limits.gateRunsPerMonth} />
            </div>
          </>
        )}

        <div className="flex items-center gap-2 text-xs">
          <span>Billing</span>
          {(["MONTHLY", "YEARLY"] as const).map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={interval === value}
              onClick={() => setInterval_(value)}
              className={`rounded-full border px-3 py-1 ${interval === value ? "border-primary bg-primary/10" : "border-border"}`}
            >
              {value === "MONTHLY" ? "Monthly" : "Yearly"}
            </button>
          ))}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          {PLANS.filter((p) => p.code === "TEAM" || p.code === "BUSINESS").map((plan) => {
            const price = interval === "MONTHLY" ? plan.monthly : plan.yearly;
            const isCurrent = b?.plan === plan.code;
            return (
              <div key={plan.code} className="rounded-xl border border-border p-4 text-sm">
                <div className="font-medium">{plan.name}</div>
                <div className="text-2xl font-semibold">
                  {formatPrice(price)}
                  <span className="text-xs font-normal text-muted-foreground">
                    {interval === "MONTHLY" ? " / month" : " / year"}
                  </span>
                </div>
                <ul className="mt-2 space-y-1 text-xs text-muted-foreground">
                  <li>{formatLimit(plan.gatedSystems)} gated systems</li>
                  <li>{formatLimit(plan.editors)} editor seats (viewers are free)</li>
                  <li>{formatLimit(plan.gateRunsPerMonth)} CI gate runs</li>
                  {plan.features.map((f) => (
                    <li key={f.label} className={f.available ? "" : "italic"}>
                      {f.label}
                    </li>
                  ))}
                </ul>
                {subscribed ? (
                  <p className="mt-3 text-xs text-muted-foreground">
                    {isCurrent ? "Current plan" : "Change plan in Manage billing"}
                  </p>
                ) : (
                  <Button
                    className="mt-3 w-full"
                    disabled={checkout.isPending}
                    onClick={() => checkout.mutate(plan.code as "TEAM" | "BUSINESS")}
                  >
                    {`Upgrade to ${plan.name}`}
                  </Button>
                )}
              </div>
            );
          })}
        </div>
        {checkout.isError && <p className="text-xs text-destructive">{checkoutError(checkout.error)}</p>}

        {paid && (
          <Button variant="outline" onClick={() => portal.mutate()} disabled={portal.isPending}>
            Manage billing
          </Button>
        )}
        {portal.isError && <p className="text-xs text-destructive">The billing portal could not be opened.</p>}
      </CardContent>
    </Card>
  );
}
