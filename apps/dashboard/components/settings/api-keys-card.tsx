"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { API_KEY_SCOPES, DEFAULT_API_KEY_SCOPE, apiKeyScopeLabel } from "@/lib/api-key-scope";
import { releaseGateWorkflow } from "@/lib/onboarding";
import { siteConfig } from "@/lib/site-config";
import type { ApiKeyCreated, ApiKeyScope } from "@/lib/types";

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString() : "never";
}

export function ApiKeysCard() {
  const qc = useQueryClient();
  const keys = useQuery({ queryKey: ["api-keys"], queryFn: api.apiKeys.list, retry: false });
  const [name, setName] = useState("");
  const [scope, setScope] = useState<ApiKeyScope>(DEFAULT_API_KEY_SCOPE);
  const [created, setCreated] = useState<ApiKeyCreated | null>(null);
  const [copied, setCopied] = useState(false);

  const create = useMutation({
    mutationFn: () => api.apiKeys.create(name.trim() || "API key", scope),
    onSuccess: (key) => {
      setCreated(key);
      setCopied(false);
      setName("");
      setScope(DEFAULT_API_KEY_SCOPE);
      void qc.invalidateQueries({ queryKey: ["api-keys"] });
    },
  });
  const revoke = useMutation({
    mutationFn: (id: string) => api.apiKeys.revoke(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["api-keys"] }),
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
        <CardTitle>API keys</CardTitle>
        <CardDescription>
          Keys let CI call the release gate. A release-gate key can do nothing else, so a leaked key cannot change
          your systems. A full-access key acts with the permissions of the person who created it; create one only
          for automation that needs it. Admins and AI engineering leads only.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate();
          }}
        >
          <label className="text-xs">
            Key name
            <input
              value={name}
              maxLength={80}
              onChange={(e) => setName(e.target.value)}
              placeholder="GitHub Actions"
              className="mt-1 block w-56 rounded-lg border border-border bg-background px-3 py-2 text-sm"
            />
          </label>
          <label className="text-xs">
            Access
            <select
              value={scope}
              onChange={(e) => setScope(e.target.value as ApiKeyScope)}
              className="mt-1 block w-56 rounded-lg border border-border bg-background px-3 py-2 text-sm"
            >
              {API_KEY_SCOPES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
          <Button type="submit" size="sm" disabled={create.isPending}>
            Create key
          </Button>
        </form>
        <p className="text-xs text-muted-foreground">{API_KEY_SCOPES.find((s) => s.value === scope)?.hint}</p>
        {create.isError && (
          <p className="text-xs text-destructive">Could not create a key. You need the admin or AI engineering lead role.</p>
        )}

        {created && (
          <div className="space-y-2 rounded-lg border border-border bg-muted px-3 py-3 text-xs">
            <p className="font-medium">Copy this key now. You won&apos;t see it again.</p>
            <div className="flex gap-2">
              <input
                readOnly
                value={created.key}
                onFocus={(e) => e.currentTarget.select()}
                aria-label="New API key"
                className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-xs"
              />
              <Button type="button" size="sm" variant="outline" onClick={() => copy(created.key)}>
                {copied ? "Copied" : "Copy"}
              </Button>
            </div>
            <Button type="button" size="sm" variant="ghost" onClick={() => setCreated(null)}>
              I&apos;ve saved it
            </Button>
          </div>
        )}

        {keys.isError ? (
          <p className="text-xs text-muted-foreground">Keys are visible to admins and AI engineering leads.</p>
        ) : (
          <ul className="text-sm">
            {(keys.data ?? []).map((k) => (
              <li key={k.id} className="flex flex-wrap items-center justify-between gap-2 border-b border-border py-2">
                <span>
                  <strong>{k.name}</strong>{" "}
                  <span className="font-mono text-xs text-muted-foreground">{k.prefix}…</span>{" "}
                  <span className="text-xs text-muted-foreground">· {apiKeyScopeLabel(k.scope)}</span>
                  <span className="block text-xs text-muted-foreground">
                    Created {formatDate(k.createdAt)} · Last used {formatDate(k.lastUsedAt)}
                  </span>
                </span>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={revoke.isPending}
                  onClick={() => {
                    if (window.confirm(`Revoke "${k.name}"? Anything using it will stop working.`)) {
                      revoke.mutate(k.id);
                    }
                  }}
                >
                  Revoke
                </Button>
              </li>
            ))}
            {keys.data && keys.data.length === 0 && (
              <li className="py-2 text-xs text-muted-foreground">No keys yet.</li>
            )}
          </ul>
        )}

        <pre className="overflow-x-auto rounded-lg bg-muted px-3 py-3 text-xs">{releaseGateWorkflow(undefined, siteConfig.appUrl)}</pre>
      </CardContent>
    </Card>
  );
}
