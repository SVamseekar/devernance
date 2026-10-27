"use client";

import { useId, useState, type ReactNode } from "react";
import { Check, Copy } from "lucide-react";

import { cn } from "@/lib/utils";

export type CodeTab = { label: string; filename?: string; code: string };

/** Storyboard frame 09: tabbed code sample with line numbers and a copy button. */
/** Minimal highlighter: YAML/JSON keys, strings after keys, and # comments. */
function Line({ text }: { text: string }) {
  const comment = text.match(/^(\s*)(#.*)$/);
  if (comment) {
    return (
      <>
        {comment[1]}
        <span className="text-white/55">{comment[2]}</span>
      </>
    );
  }
  // A key is followed by ":" and a space or the line end, so URLs like "http://…" stay plain.
  const keyed = text.match(/^(\s*-?\s*)("?[A-Za-z_][\w-]*"?)(:)(\s.*|)$/);
  if (keyed) {
    return (
      <>
        {keyed[1]}
        <span className="text-[#ff7a9a]">{keyed[2]}</span>
        <span className="text-white/50">{keyed[3]}</span>
        <span className="text-[#e6ebff]">{keyed[4]}</span>
      </>
    );
  }
  return <span className="text-[#c9d4ff]">{text || " "}</span>;
}

/** `compact` drops the filename row and tightens lines, for layouts where the block must fit one screen. */
export function CodeTabs({ tabs, status, compact }: { tabs: CodeTab[]; status?: ReactNode; compact?: boolean }) {
  const baseId = useId();
  const [active, setActive] = useState(0);
  const [copied, setCopied] = useState(false);
  const tab = tabs[active];

  async function copy() {
    try {
      await navigator.clipboard.writeText(tab.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard can be blocked; the code stays selectable.
    }
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
    event.preventDefault();
    const next = (active + (event.key === "ArrowRight" ? 1 : tabs.length - 1)) % tabs.length;
    setActive(next);
    document.getElementById(`${baseId}-tab-${next}`)?.focus();
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-white/10 bg-[#070d22] shadow-2xl shadow-black/40">
      <div className="flex items-center justify-between gap-2 border-b border-white/10 pr-2">
        <div role="tablist" aria-label="Code samples" className="flex min-w-0 overflow-x-auto">
          {tabs.map((t, i) => (
            <button
              key={t.label}
              id={`${baseId}-tab-${i}`}
              role="tab"
              type="button"
              aria-selected={i === active}
              aria-controls={`${baseId}-panel`}
              tabIndex={i === active ? 0 : -1}
              onClick={() => setActive(i)}
              onKeyDown={onKeyDown}
              className={cn(
                "shrink-0 border-b-2 px-3 py-3 text-sm font-medium transition-colors sm:px-5",
                i === active
                  ? "border-periwinkle bg-white/[0.04] text-white"
                  : "border-transparent text-on-dark-muted hover:text-white",
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={copy}
          className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-md border border-white/10 px-2.5 text-xs text-on-dark-muted hover:text-white"
          aria-label={copied ? "Copied" : "Copy code"}
        >
          {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
          <span className="hidden sm:inline">{copied ? "Copied" : "Copy"}</span>
        </button>
      </div>
      <div id={`${baseId}-panel`} role="tabpanel" aria-labelledby={`${baseId}-tab-${active}`}>
        {tab.filename && !compact ? (
          <p className="border-b border-white/5 px-5 py-2 font-mono text-xs text-on-dark-muted">{tab.filename}</p>
        ) : null}
        <pre className={cn("overflow-x-auto font-mono text-[13px]", compact ? "px-4 py-4 leading-[1.4rem]" : "p-5 leading-6")} tabIndex={0}>
          <code>
            {tab.code.split("\n").map((line, i) => (
              <span key={i} className="block">
                <span aria-hidden="true" className={cn("inline-block w-5 text-right text-white/45 select-none", compact ? "mr-4" : "mr-5")}>{i + 1}</span>
                <Line text={line} />
              </span>
            ))}
          </code>
        </pre>
        {status ? <div className="flex justify-end px-5 pb-4">{status}</div> : null}
      </div>
    </div>
  );
}
