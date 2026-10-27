import type { ApiKeyScope } from "@/lib/types";

/** Mirrors the API: new keys are release-gate only unless full access is asked for. */
export const DEFAULT_API_KEY_SCOPE: ApiKeyScope = "RELEASE_GATE";

export const API_KEY_SCOPES: { value: ApiKeyScope; label: string; hint: string }[] = [
  {
    value: "RELEASE_GATE",
    label: "Release gate only",
    hint: "Can only read the CI release gate. Use this for pipelines.",
  },
  {
    value: "FULL",
    label: "Full access",
    hint: "Acts with your role on every endpoint (never keys, invites or workspace settings).",
  },
];

/** Keys created before scopes existed come back as FULL from the API; a missing value means the same. */
export function apiKeyScopeLabel(scope: ApiKeyScope | undefined): string {
  return (API_KEY_SCOPES.find((s) => s.value === scope) ?? API_KEY_SCOPES[1]).label;
}
