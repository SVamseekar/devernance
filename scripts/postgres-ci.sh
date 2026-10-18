#!/usr/bin/env bash
# Starts a Postgres+pgvector container, runs @Tag("postgres") integration tests, tears down.
set -euo pipefail

CONTAINER=devernance-ci-postgres

docker run -d --name "$CONTAINER" \
  -e POSTGRES_DB=eu_ai_assurance \
  -e POSTGRES_USER=eu_ai_assurance \
  -e POSTGRES_PASSWORD=eu_ai_assurance \
  -p 5433:5432 \
  pgvector/pgvector:pg16

cleanup() {
  docker rm -f "$CONTAINER" 2>/dev/null || true
}
trap cleanup EXIT

until docker exec "$CONTAINER" pg_isready -U eu_ai_assurance; do sleep 1; done

DATABASE_URL=jdbc:postgresql://localhost:5433/eu_ai_assurance \
DATABASE_USERNAME=eu_ai_assurance \
DATABASE_PASSWORD=eu_ai_assurance \
EVAL_CALLBACK_SECRET=test-eval-callback-secret \
mvn -f services/api/pom.xml verify

echo "Postgres CI tests passed."
