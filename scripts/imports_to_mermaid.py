"""Collapse a madge import graph into a folder-level Mermaid flowchart.

Reads `madge --json` output (file -> imported files) on stdin, groups files
into dashboard areas (route groups, API routes, components, lib, hooks, ...)
and prints one edge per group pair, labelled with the number of imports.
Output is derived only from the source imports.

Usage (from apps/dashboard):
  npx madge --extensions ts,tsx --ts-config tsconfig.json --json \
    app components lib hooks context middleware.ts \
    | python3 ../../scripts/imports_to_mermaid.py
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter


def group(path: str) -> str:
    parts = path.split("/")
    if parts[0] == "app":
        if len(parts) == 2:
            return "app (root layout)"
        if parts[1] in ("(dashboard)", "api"):
            return f"app/{parts[1]}"
        return "app (public pages)"
    if parts[0] == "components" and len(parts) > 2:
        return f"components/{parts[1]}"
    return parts[0].removesuffix(".ts")


def _id(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9_]", "_", name)


def render(graph: dict[str, list[str]]) -> str:
    edges: Counter[tuple[str, str]] = Counter()
    files: Counter[str] = Counter()
    for src, deps in graph.items():
        files[group(src)] += 1
        for dep in deps:
            a, b = group(src), group(dep)
            if a != b:
                edges[(a, b)] += 1

    lines = ["flowchart LR"]
    for name, count in sorted(files.items()):
        lines.append(f'  {_id(name)}["{name}<br/>{count} files"]')
    for (a, b), count in sorted(edges.items()):
        lines.append(f"  {_id(a)} -->|{count}| {_id(b)}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.stdout.write(render(json.load(sys.stdin)))
