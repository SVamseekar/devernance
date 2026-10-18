"""Render GitHub Actions workflow files as Mermaid flowcharts.

Reads each workflow's triggers, jobs, `needs:` edges and job-level `if:`
conditions, and prints one Mermaid flowchart per workflow. Output is
deterministic: it is derived only from the YAML, so re-running it after a
workflow change keeps the pipeline diagram in sync.

Usage: python scripts/workflow_to_mermaid.py .github/workflows/*.yml
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml


def _node_id(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9_]", "_", name)


def _label(text: str) -> str:
    return text.replace('"', "'")


def _triggers(on: object) -> list[str]:
    if isinstance(on, str):
        return [on]
    if isinstance(on, list):
        return [str(t) for t in on]
    if isinstance(on, dict):
        out = []
        for event, cfg in on.items():
            detail = ""
            if isinstance(cfg, dict):
                for key in ("branches", "tags"):
                    if key in cfg:
                        detail = f" {key}: {', '.join(map(str, cfg[key]))}"
                        break
            out.append(f"{event}{detail}")
        return out
    return []


def render(path: Path) -> str:
    # PyYAML parses the bare key `on` as boolean True.
    doc = yaml.safe_load(path.read_text())
    on = doc.get("on", doc.get(True))
    jobs: dict = doc.get("jobs", {}) or {}

    lines = ["flowchart LR"]
    trigger_ids = []
    for i, trig in enumerate(_triggers(on)):
        tid = f"trigger_{i}"
        trigger_ids.append(tid)
        lines.append(f'  {tid}(["{_label(trig)}"])')

    for job_id, job in jobs.items():
        name = (job or {}).get("name", job_id)
        cond = (job or {}).get("if")
        text = f"{name}<br/><i>if: {cond}</i>" if cond else name
        lines.append(f'  {_node_id(job_id)}["{_label(str(text))}"]')

    for job_id, job in jobs.items():
        needs = (job or {}).get("needs")
        if not needs:
            for tid in trigger_ids:
                lines.append(f"  {tid} --> {_node_id(job_id)}")
            continue
        for dep in [needs] if isinstance(needs, str) else needs:
            lines.append(f"  {_node_id(dep)} --> {_node_id(job_id)}")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    for arg in argv:
        path = Path(arg)
        name = yaml.safe_load(path.read_text()).get("name", path.stem)
        print(f"### {name} (`{path.as_posix()}`)\n")
        print("```mermaid")
        print(render(path), end="")
        print("```\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
