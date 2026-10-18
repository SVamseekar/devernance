"""Render a resolved Docker Compose config as a Mermaid flowchart.

Reads the JSON printed by `docker compose ... config --format json` on stdin
and prints a Mermaid diagram of services, published ports, named volumes,
`depends_on` edges and profiles. Output is derived only from the Compose
files, so re-running it after a Compose change keeps the diagram in sync.

Usage:
  docker compose -f infra/docker-compose.yml --profile full config --format json \
    | python3 scripts/compose_to_mermaid.py
"""

from __future__ import annotations

import json
import re
import sys


def _id(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9_]", "_", name)


def render(config: dict) -> str:
    services: dict = config.get("services", {})
    lines = ["flowchart LR"]

    for name, svc in sorted(services.items()):
        image = svc.get("image") or "built from " + svc.get("build", {}).get("dockerfile", "Dockerfile")
        ports = [
            f"{p['published']}:{p['target']}"
            for p in svc.get("ports", [])
            if p.get("published")
        ]
        profiles = svc.get("profiles")
        parts = [f"<b>{name}</b>", image]
        if ports:
            parts.append("ports " + ", ".join(ports))
        parts.append("profiles: " + ", ".join(profiles) if profiles else "default stack")
        lines.append(f'  {_id(name)}["{"<br/>".join(parts)}"]')

    for vol in sorted(config.get("volumes", {})):
        lines.append(f'  vol_{_id(vol)}[("volume: {vol}")]')

    for name, svc in sorted(services.items()):
        for dep, cfg in sorted((svc.get("depends_on") or {}).items()):
            cond = cfg.get("condition", "").replace("service_", "")
            label = f"|\"depends on ({cond})\"|" if cond else "|depends on|"
            lines.append(f"  {_id(name)} -->{label} {_id(dep)}")
        for v in svc.get("volumes", []):
            if v.get("type") == "volume":
                lines.append(f"  {_id(name)} -.->|\"mounts {v['target']}\"| vol_{_id(v['source'])}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.stdout.write(render(json.load(sys.stdin)))
