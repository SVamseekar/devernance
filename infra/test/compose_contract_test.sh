#!/usr/bin/env bash
# Contract checks for infra/docker-compose.prod.yml against infra/.env.prod.example and the API's properties.
# Needs the docker compose CLI only (no daemon).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/docker-compose.prod.yml"
ENV_EXAMPLE="$ROOT/infra/.env.prod.example"

[[ -f "$COMPOSE_FILE" ]] || { echo "compose_contract_test: missing $COMPOSE_FILE" >&2; exit 1; }
[[ -f "$ENV_EXAMPLE" ]] || { echo "compose_contract_test: missing $ENV_EXAMPLE" >&2; exit 1; }

rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_EXAMPLE" config --format json > "$rendered"

python3 -I - "$rendered" "$COMPOSE_FILE" "$ENV_EXAMPLE" "$ROOT/services/api/src/main/resources" <<'PY'
import json, pathlib, re, sys

rendered, compose_path, env_path, resources = sys.argv[1:5]
config = json.load(open(rendered))
services = config["services"]
failures = []

def check(condition, message):
    if not condition:
        failures.append(message)

expected = {"postgres", "api", "dashboard", "cloudflared", "backup"}
check(set(services) == expected, f"services are {sorted(services)}, expected {sorted(expected)}")

for name, svc in services.items():
    check(not svc.get("ports"), f"{name} publishes host ports {svc.get('ports')}")
    check(svc.get("restart") == "unless-stopped", f"{name} restart is {svc.get('restart')!r}")
    check(svc.get("mem_limit"), f"{name} has no mem_limit")
    opts = (svc.get("logging") or {}).get("options") or {}
    check(opts.get("max-size") and opts.get("max-file"), f"{name} has no log rotation")
    check(not svc.get("env_file"), f"{name} uses env_file; list variables explicitly")
    image = svc.get("image", "")
    check(":" in image and not image.endswith(":latest"), f"{name} image {image!r} is not pinned")

# Docker's default /dev/shm (64 MB) breaks parallel pgvector HNSW index builds.
shm = services["postgres"].get("shm_size")
check(shm is not None and int(shm) >= 256 * 1024 * 1024, f"postgres shm_size is {shm!r}, needs >= 256m")

def env(name):
    return set((services.get(name, {}).get("environment") or {}).keys())

# Least privilege: secrets reach only the service that uses them.
edge_and_backup = {k for k in env("api") if k.startswith(("CLOUDFLARE_", "BACKUP_"))}
check(not edge_and_backup, f"api receives edge/backup secrets {sorted(edge_and_backup)}")
backup_extra = {k for k in env("backup") if not k.startswith(("BACKUP_", "PG"))}
check(not backup_extra, f"backup receives unrelated variables {sorted(backup_extra)}")
dash_db = {k for k in env("dashboard") if "DATABASE" in k or "POSTGRES" in k or k.startswith("DODO_")}
check(not dash_db, f"dashboard receives API secrets {sorted(dash_db)}")
check(env("cloudflared") <= {"TUNNEL_TOKEN"}, f"cloudflared environment {sorted(env('cloudflared'))}")

# The API trusts the forwarded client IP because only the dashboard BFF can reach it.
check(services["api"].get("environment", {}).get("ASSURANCE_SECURITY_TRUST_CLIENT_IP_HEADER") == "true",
      "api must trust the client-IP header (only the dashboard can reach it)")
check(services["api"]["environment"].get("SPRING_PROFILES_ACTIVE") == "postgres", "api must run the postgres profile")
check("ASSURANCE_SEED_DEMO_USERS" not in env("api"), "api must not set ASSURANCE_SEED_DEMO_USERS")
# Generated eval metrics are a local demo aid; production runs wait for the signed callback.
check("ASSURANCE_EVAL_SYNTHETIC_METRICS" not in env("api"), "api must not set ASSURANCE_EVAL_SYNTHETIC_METRICS")

# Every API property placeholder is either passed to the api container or a deliberate default.
defaults_ok = {
    "ASSURANCE_AUTH_RATE_PER_EMAIL", "ASSURANCE_AUTH_RATE_PER_IP", "ASSURANCE_INVITE_TTL_HOURS",
    "ASSURANCE_REFRESH_REUSE_GRACE_SECONDS", "ASSURANCE_EVGRAPH_ALLOW_RUNTIME_INSTALL",
    "ASSURANCE_OPERATOR_TENANT_ID", "ASSURANCE_SEED_DEMO_USERS", "ASSURANCE_EVAL_SYNTHETIC_METRICS",
}
placeholders = set()
for props in pathlib.Path(resources).glob("application*.properties"):
    placeholders |= set(re.findall(r"\$\{([A-Z][A-Z0-9_]+)", props.read_text()))
missing = sorted(placeholders - defaults_ok - env("api"))
check(not missing, f"api does not receive {missing}")

# Every ${VAR} the compose file interpolates is documented in the env example.
compose_vars = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", pathlib.Path(compose_path).read_text()))
documented = set(re.findall(r"^([A-Z][A-Z0-9_]*)=", pathlib.Path(env_path).read_text(), re.M))
undocumented = sorted(compose_vars - documented)
check(not undocumented, f".env.prod.example lacks {undocumented}")

if failures:
    print("compose_contract_test: FAIL", file=sys.stderr)
    for f in failures:
        print("  - " + f, file=sys.stderr)
    sys.exit(1)
print(f"compose_contract_test: ok ({len(services)} services, {len(env('api'))} api variables)")
PY
