"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api";
import type { DriftEvent } from "@/lib/types";

interface DriftEventActionsProps {
  event: DriftEvent;
  onAct: (status: "ACKNOWLEDGED" | "RESOLVED", note: string) => Promise<void>;
}

/** Acknowledge or resolve one drift event. Both need a note; only compliance or an admin can resolve a breach. */
export function DriftEventActions({ event, onAct }: DriftEventActionsProps) {
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const noteId = `drift-note-${event.id}`;

  async function act(status: "ACKNOWLEDGED" | "RESOLVED") {
    setBusy(true);
    setError(null);
    try {
      await onAct(status, note.trim());
      setNote("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "The update failed. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-1.5 pt-1">
      <label htmlFor={noteId} className="text-[10px] font-medium text-muted-foreground">
        What was done about it
      </label>
      <textarea
        id={noteId}
        rows={2}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="e.g. Column dropped at ingestion in PR #412; extract re-validated clean"
        className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-[11px] focus:outline-none focus:ring-2 focus:ring-ring/50"
      />
      {event.severity === "BREACH" && (
        <p className="text-[10px] text-muted-foreground">Only compliance or an admin can resolve a breach.</p>
      )}
      {error && <p role="alert" className="text-[10px] text-destructive">{error}</p>}
      <div className="flex justify-end gap-2">
        {event.status === "OPEN" && (
          <button
            type="button"
            disabled={busy || !note.trim()}
            onClick={() => act("ACKNOWLEDGED")}
            className="px-2 py-1 bg-muted hover:bg-muted-foreground/10 border border-border text-[10px] font-medium rounded-md cursor-pointer transition-colors text-foreground disabled:opacity-50"
          >
            Acknowledge
          </button>
        )}
        <button
          type="button"
          disabled={busy || !note.trim()}
          onClick={() => act("RESOLVED")}
          className="px-2.5 py-1 bg-primary hover:bg-primary/95 text-[10px] font-medium text-primary-foreground rounded-md cursor-pointer transition-colors shadow-xs disabled:opacity-50"
        >
          Resolve
        </button>
      </div>
    </div>
  );
}
