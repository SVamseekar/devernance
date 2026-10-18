# Scripts

## `check_commit_attribution.py`

Solo-maintainer policy: reject `Co-authored-by` / `Co-committed-by` trailers
and known agent emails in commit messages and PR bodies. Used by the
commit-msg hook and the `No co-authors` CI job.

```bash
python3 tests/test_check_commit_attribution.py
python3 scripts/check_commit_attribution.py --from-ref origin/main --to-ref HEAD
```

Dependabot `Signed-off-by` is allowed.

## `ci-release-gate.sh` (Part 8)

Call Devernance from a deploy pipeline and fail when the system is BLOCKED.

```bash
ASSURANCE_API_BASE=http://localhost:8080 \
API_KEY=00000000-0000-0000-0000-000000000a01 \
SYSTEM_ID=<uuid> \
./scripts/ci-release-gate.sh
```

| Exit | Meaning |
|---|---|
| 0 | PASS |
| 1 | BLOCKED (or REVIEW when `FAIL_ON_REVIEW=1`) |
| 2 | REVIEW |
| 3 | Missing env / usage |
| 4 | HTTP / transport / parse error |

Requires `curl` and either `jq` or `python3`. Full contract: `docs/OPS.md`.

## `postgres-ci.sh`

Local/CI helpers for Postgres-backed smoke (see `services/api/scripts` and README).

## `compose-with-minio.sh` (Part 9)

Starts Compose with the `minio` profile and storage env pointed at MinIO.

```bash
./scripts/compose-with-minio.sh
```

## `compose-evidence-smoke.sh` (Part 9)

Against a running API: login → evidence document → RAG query.

```bash
ASSURANCE_API_BASE=http://localhost:8080 ./scripts/compose-evidence-smoke.sh
```

Local bootstrap user: `compliance@example.com` / `dev-local-password-only` (never production).

## `public-claims-evgraph.sh`

Runs Evgraph on four EU public-claims packs (Getsafe, auxmoney, softgarden, Retorio). Not customers. Expects promotion `--gate --strict` exit 1 and dataset `--gate` exit 1.

```bash
./scripts/public-claims-evgraph.sh
```

Requires the Evgraph CLI (`pip install evgraph` or sibling `../evgraph/.venv`). Writes markdown/JSON next to the packs. If a sibling GTM tree exists it copies leave-behinds there — that tree is **not** this repository. See `docs/PUBLIC_CLAIMS.md`.

## `terraform-validate.sh` (Part 9)

`fmt -check` + `init -backend=false` + `validate` for `infra/terraform` (null provider; no cloud credentials).
