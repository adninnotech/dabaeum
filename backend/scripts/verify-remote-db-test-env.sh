#!/usr/bin/env bash
set -euo pipefail

: "${DABAEUM_SSH_PASSWORD:?DABAEUM_SSH_PASSWORD is required}"
: "${DABAEUM_DB_PASSWORD:?DABAEUM_DB_PASSWORD is required}"

KNOWN_HOSTS="${DABAEUM_SSH_KNOWN_HOSTS:-$HOME/.ssh/known_hosts}"
test -f "$KNOWN_HOSTS"

ssh-keygen \
  -F '[118.45.182.100]:22880' \
  -f "$KNOWN_HOSTS" >/dev/null

if [[ -n "${SPRING_DATASOURCE_URL:-}" ]]; then
  echo "SPRING_DATASOURCE_URL override is forbidden for integration tests." >&2
  exit 1
fi

if [[ -n "${SPRING_FLYWAY_URL:-}" ]]; then
  echo "SPRING_FLYWAY_URL override is forbidden for integration tests." >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:15432 -sTCP:LISTEN >/dev/null; then
    echo "Local port 15432 is already occupied." >&2
    exit 1
  fi
fi
