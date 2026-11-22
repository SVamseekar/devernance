"use client";

// Governance runtime: what is governed, what agents asked to do, what waits for a person, and what drifted.
// Policies are applied with the CLI (`devernance apply`); this page reads them and answers approvals.
// See docs/GOVERNANCE_PLATFORM.md.

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { api, ApiError } from "@/lib/api";
import {
  DECISION_TONE,
  attentionOrder,
  canAnswer,
  decisionOutcome,
  describeFacts,
} from "@/lib/governance-view";
import type { GovernanceApproval, GovernanceDecisionStatus } from "@/lib/types";
import { formatDate } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, Clock, ShieldCheck, TerminalSquare } from "lucide-react";
import { useState } from "react";

type Answer = { approval: GovernanceApproval; approve: boolean } | null;

const STATUS_FILTERS: (GovernanceDecisionStatus | "ALL")[] = ["ALL", "ALLOW", "REVIEW", "DENY"];

function DecisionBadge({ status }: { status: GovernanceDecisionStatus }) {
  return (
    <span className={`inline-flex rounded px-1.5 py-0.5 text-[10px] font-semibold ${DECISION_TONE[status]}`}>
      {status}
    </span>
  );
}

function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof ApiError ? error.message : "Could not load governance data.";
  return <p className="text-xs text-destructive">{message}</p>;
}

function GetStarted() {
  return (
    <div className="space-y-2 border border-dashed border-border px-4 py-5 text-xs text-muted-foreground">
      <p className="flex items-center gap-2 font-medium text-foreground">
        <TerminalSquare className="h-4 w-4" /> No governed systems yet
      </p>
      <p>Governance is defined as code in your repository and applied with the CLI:</p>
      <pre className="overflow-x-auto bg-muted/40 px-3 py-2 font-mono text-[11px] text-foreground">
        {`pip install ./sdk/python        # from the Devernance repository
devernance login --server <this workspace's API URL>
devernance init                 # writes governance/system.yaml, policy.yaml, tests.yaml
devernance validate && devernance test
devernance apply`}
      </pre>
      <p>Use a RUNTIME API key inside agents and a RELEASE_GATE key in CI (Workspace settings, API keys).</p>
    </div>
  );
}

export default function GovernancePage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<GovernanceDecisionStatus | "ALL">("ALL");
  const [answer, setAnswer] = useState<Answer>(null);
  const [rationale, setRationale] = useState("");

  const me = useQuery({ queryKey: ["me"], queryFn: api.me, retry: false, staleTime: 5 * 60_000 });
  const status = useQuery({ queryKey: ["governance", "status"], queryFn: api.governance.status, refetchInterval: 30_000 });
  const pending = useQuery({
    queryKey: ["governance", "approvals", "PENDING"],
    queryFn: () => api.governance.approvals("PENDING"),
    refetchInterval: 10_000,
  });
  const decisions = useQuery({
    queryKey: ["governance", "decisions", filter],
    queryFn: () => api.governance.decisions(filter === "ALL" ? undefined : filter),
    refetchInterval: 15_000,
  });

  const decide = useMutation({
    mutationFn: ({ approval, approve, note }: { approval: GovernanceApproval; approve: boolean; note: string }) =>
      approve ? api.governance.approve(approval.id, note) : api.governance.reject(approval.id, note),
    onSuccess: () => {
      setAnswer(null);
      setRationale("");
      queryClient.invalidateQueries({ queryKey: ["governance"] });
      queryClient.invalidateQueries({ queryKey: ["audit"] });
    },
  });

  const systems = attentionOrder(status.data ?? []);
  const role = me.data?.role;
  const rejecting = answer !== null && !answer.approve;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Governed systems</CardTitle>
          <CardDescription>
            The approved state applied with <code className="font-mono">devernance apply</code>, checked against
            its policy and against what deployments report they run.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <ErrorNote error={status.error} />
          {status.isLoading ? (
            <p className="text-xs text-muted-foreground">Loading...</p>
          ) : systems.length === 0 && !status.error ? (
            <GetStarted />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="text-[10px] uppercase tracking-widest text-muted-foreground">
                  <tr>
                    <th className="py-2 pr-4 font-semibold">System</th>
                    <th className="py-2 pr-4 font-semibold">Model</th>
                    <th className="py-2 pr-4 font-semibold">Policy</th>
                    <th className="py-2 pr-4 font-semibold">Validation</th>
                    <th className="py-2 pr-4 font-semibold">Drift</th>
                    <th className="py-2 pr-4 font-semibold">24h allow / deny / review</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {systems.map((s) => {
                    const drifted = s.drift.filter((d) => !d.inSync);
                    return (
                      <tr key={s.systemId} className="align-top">
                        <td className="py-2.5 pr-4">
                          <p className="font-medium text-foreground">{s.system}</p>
                          <p className="text-[10px] text-muted-foreground">
                            v{s.systemVersion} · {s.tools.length} tool(s)
                          </p>
                        </td>
                        <td className="py-2.5 pr-4 font-mono text-[11px]">{s.model ?? "-"}</td>
                        <td className="py-2.5 pr-4">
                          {s.policy ? `${s.policy.name} v${s.policy.version}` : "none"}
                          <p className="text-[10px] text-muted-foreground">runtime: {s.runtimeEnforcement}</p>
                        </td>
                        <td className="py-2.5 pr-4">
                          {s.validation.valid ? (
                            <span className="flex items-center gap-1 text-emerald-700 dark:text-emerald-400">
                              <CheckCircle2 className="h-3.5 w-3.5" /> valid
                            </span>
                          ) : (
                            <div className="space-y-1">
                              {s.validation.findings
                                .filter((f) => f.severity === "ERROR")
                                .map((f) => (
                                  <p key={f.code + f.subject} className="text-red-700 dark:text-red-400">
                                    {f.code}: {f.subject}
                                  </p>
                                ))}
                            </div>
                          )}
                        </td>
                        <td className="py-2.5 pr-4">
                          {s.drift.length === 0 ? (
                            <span className="text-muted-foreground">not reported</span>
                          ) : drifted.length === 0 ? (
                            <span className="text-emerald-700 dark:text-emerald-400">in sync</span>
                          ) : (
                            drifted.map((d) => (
                              <div key={d.environment} className="space-y-0.5 text-amber-800 dark:text-amber-400">
                                <p className="flex items-center gap-1 font-medium">
                                  <AlertTriangle className="h-3.5 w-3.5" /> {d.environment}
                                </p>
                                {d.differences.map((diff) => (
                                  <p key={diff.property} className="text-[10px]">
                                    {diff.property}: {diff.approved} → {diff.observed}
                                  </p>
                                ))}
                              </div>
                            ))
                          )}
                        </td>
                        <td className="py-2.5 pr-4 tabular-nums">
                          {s.decisionsLast24h.ALLOW} / {s.decisionsLast24h.DENY} / {s.decisionsLast24h.REVIEW}
                          {s.pendingApprovals > 0 && (
                            <p className="text-[10px] text-amber-800 dark:text-amber-400">
                              {s.pendingApprovals} waiting for approval
                            </p>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Waiting for approval</CardTitle>
          <CardDescription>
            Actions an agent may not take alone. Nothing runs until a person with a role the policy names approves
            it here. An API key can never approve.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          <ErrorNote error={pending.error} />
          {(pending.data ?? []).length === 0 ? (
            <p className="border border-dashed border-border py-5 text-center text-xs text-muted-foreground">
              Nothing is waiting for approval.
            </p>
          ) : (
            (pending.data ?? []).map((approval) => {
              const allowed = canAnswer(role, approval.approverRoles);
              return (
                <div
                  key={approval.id}
                  className="flex flex-wrap items-center justify-between gap-3 border border-border bg-card px-4 py-3"
                >
                  <div className="min-w-0 space-y-0.5">
                    <p className="flex items-center gap-2 text-sm font-medium text-foreground">
                      <ShieldCheck className="h-4 w-4 text-amber-500" />
                      {approval.reason}: <span className="font-mono text-xs">{approval.action}</span>
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {approval.system} · agent {approval.agent} · {describeFacts(approval.resource, approval.context)}
                    </p>
                    <p className="flex items-center gap-1 text-[10px] text-muted-foreground" suppressHydrationWarning>
                      <Clock className="h-3 w-3" /> requested {formatDate(approval.requestedAt)} · expires{" "}
                      {formatDate(approval.expiresAt)} · approver: {approval.approverRoles.join(" or ").replace(/_/g, " ")}
                    </p>
                  </div>
                  <div className="flex gap-1.5">
                    <Button size="sm" disabled={!allowed} onClick={() => setAnswer({ approval, approve: true })}>
                      Approve
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      disabled={!allowed}
                      onClick={() => setAnswer({ approval, approve: false })}
                    >
                      Reject
                    </Button>
                  </div>
                </div>
              );
            })
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Runtime decisions</CardTitle>
          <CardDescription>
            Every request an agent made, the policy version that answered it, and what happened next.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex gap-1.5" role="group" aria-label="Filter decisions">
            {STATUS_FILTERS.map((value) => (
              <Button
                key={value}
                size="sm"
                variant={filter === value ? "default" : "outline"}
                aria-pressed={filter === value}
                onClick={() => setFilter(value)}
              >
                {value === "ALL" ? "All" : value}
              </Button>
            ))}
          </div>
          <ErrorNote error={decisions.error} />
          {(decisions.data ?? []).length === 0 ? (
            <p className="border border-dashed border-border py-5 text-center text-xs text-muted-foreground">
              No decisions recorded yet.
            </p>
          ) : (
            <div className="divide-y divide-border border border-border">
              {(decisions.data ?? []).map((d) => (
                <div key={d.id ?? d.createdAt} className="flex flex-wrap items-start justify-between gap-3 px-4 py-2.5">
                  <div className="min-w-0 space-y-0.5">
                    <p className="flex items-center gap-2 text-xs">
                      <DecisionBadge status={d.status} />
                      <span className="font-mono">{d.action}</span>
                      <span className="text-muted-foreground">
                        {d.system} · agent {d.agent}
                      </span>
                    </p>
                    <p className="text-xs text-muted-foreground">{d.reason}</p>
                    <p className="text-[10px] text-muted-foreground">
                      rule {d.rule}
                      {d.policy ? ` · ${d.policy.name} v${d.policy.version}` : " · no policy"}
                      {decisionOutcome(d) ? ` · ${decisionOutcome(d)}` : ""}
                    </p>
                  </div>
                  <time className="shrink-0 text-[10px] text-muted-foreground tabular-nums" suppressHydrationWarning>
                    {formatDate(d.createdAt)}
                  </time>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {answer && (
        <Modal
          isOpen
          onClose={() => {
            setAnswer(null);
            setRationale("");
            decide.reset();
          }}
          title={answer.approve ? "Approve this action" : "Reject this action"}
          description={`${answer.approval.action} for ${answer.approval.system}: ${describeFacts(
            answer.approval.resource,
            answer.approval.context
          )}`}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor="governance-rationale" className="text-xs font-medium text-foreground">
                Rationale{rejecting ? " (required)" : " (optional)"}
              </label>
              <Textarea
                id="governance-rationale"
                rows={4}
                value={rationale}
                placeholder={rejecting ? "Why may this not happen?" : "What did you check?"}
                onChange={(e) => setRationale(e.target.value)}
              />
            </div>
            <ErrorNote error={decide.error} />
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setAnswer(null)}>
                Cancel
              </Button>
              <Button
                variant={answer.approve ? "default" : "destructive"}
                disabled={decide.isPending || (rejecting && rationale.trim().length === 0)}
                onClick={() => decide.mutate({ approval: answer.approval, approve: answer.approve, note: rationale.trim() })}
              >
                {answer.approve ? "Approve" : "Reject"}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
