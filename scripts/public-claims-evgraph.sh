#!/usr/bin/env bash
# Run Evgraph (library, not bundled) on the public-claims packs.
# Promotion uses --gate --strict so missing timestamps fail closed.
# Dataset uses --gate so an empty license fails closed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$ROOT/services/api/src/main/resources/public-claims"
GTM="${GTM_PUBLIC_CLAIMS:-$ROOT/../gtm/eu-ai-assurance/leave-behind/public-claims}"

find_evgraph() {
  if [[ -n "${EVGRAPH_BIN:-}" && -x "${EVGRAPH_BIN}" ]]; then
    echo "$EVGRAPH_BIN"
    return
  fi
  if command -v evgraph >/dev/null 2>&1; then
    command -v evgraph
    return
  fi
  local sibling="$ROOT/../evgraph/.venv/bin/evgraph"
  if [[ -x "$sibling" ]]; then
    echo "$sibling"
    return
  fi
  echo "evgraph CLI not found. pip install evgraph, or set EVGRAPH_BIN." >&2
  exit 3
}

EVGRAPH="$(find_evgraph)"
echo "Using $EVGRAPH"

if [[ ! -f "$PACK/catalog.json" ]]; then
  echo "missing $PACK/catalog.json" >&2
  exit 3
fi

slugs=(getsafe auxmoney softgarden retorio)
fail=0
summary=()

run_one() {
  local slug="$1"
  local dir="$PACK/$slug"
  echo
  echo "=== $slug ==="
  local promo_md="$dir/evgraph-promotion.md"
  local promo_json="$dir/evgraph-promotion.json"
  local data_md="$dir/evgraph-dataset.md"
  local data_json="$dir/evgraph-dataset.json"
  local promo_rc=0
  local data_rc=0

  # Run from the pack directory with bare file names so the committed reports
  # cite pack-relative source paths, not this machine's checkout path.
  pushd "$dir" >/dev/null
  set +e
  "$EVGRAPH" scan-promotion \
    --model-card model_card.json \
    --approval approval.json \
    --deployment deployment.json \
    --format markdown \
    --gate --strict >"$promo_md"
  promo_rc=$?
  "$EVGRAPH" scan-promotion \
    --model-card model_card.json \
    --approval approval.json \
    --deployment deployment.json \
    --format json >"$promo_json"

  "$EVGRAPH" scan-dataset-manifest dataset_manifest.csv \
    --format markdown \
    --gate >"$data_md"
  data_rc=$?
  "$EVGRAPH" scan-dataset-manifest dataset_manifest.csv \
    --format json >"$data_json"
  set -e
  popd >/dev/null

  echo "promotion --gate --strict exit=$promo_rc"
  echo "dataset --gate exit=$data_rc"
  summary+=("$slug promo=$promo_rc dataset=$data_rc")

  if [[ "$promo_rc" -ne 1 || "$data_rc" -ne 1 ]]; then
    echo "expected promotion --strict exit 1 and dataset --gate exit 1 (got promo=$promo_rc dataset=$data_rc)" >&2
    fail=1
  fi

  if [[ -d "$(dirname "$GTM")" ]]; then
    mkdir -p "$GTM/$slug"
    cp "$dir/model_card.json" "$dir/approval.json" "$dir/deployment.json" \
      "$dir/dataset_manifest.csv" "$promo_md" "$promo_json" "$data_md" "$data_json" \
      "$GTM/$slug/"
  fi
}

for slug in "${slugs[@]}"; do
  run_one "$slug"
done

echo
echo "summary:"
for line in "${summary[@]}"; do
  echo "  $line"
done

if [[ "$fail" -ne 0 ]]; then
  echo "one or more packs passed the gate — public reconstructions must fail closed" >&2
  exit 1
fi

echo "all four public-claims packs failed closed (Evgraph)."
exit 0
