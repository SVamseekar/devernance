#!/usr/bin/env bash
# Run every infra test. The Docker round-trip test runs only with RUN_DOCKER_TESTS=1 (CI sets it), so local runs
# stay light on laptops.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

shellcheck infra/backup/*.sh infra/deploy/*.sh infra/test/*.sh

infra/test/compose_contract_test.sh
infra/test/backup_test.sh
infra/test/updater_test.sh

if [[ "${RUN_DOCKER_TESTS:-0}" == "1" ]]; then
  infra/test/backup_integration_test.sh
else
  echo "run.sh: Docker round-trip test skipped (set RUN_DOCKER_TESTS=1 to run it)"
fi
