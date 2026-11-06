#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${DABAEUM_INTEGRATION_ENV_FILE:-${HOME}/.config/dabaeum/integration-test.env}"

fail() {
    echo "$1" >&2
    exit 1
}

test -f "$ENV_FILE" || fail \
    "Integration test environment file is missing: $ENV_FILE"

if [[ -L "$ENV_FILE" ]]; then
    fail "Integration test environment file must not be a symlink: $ENV_FILE"
fi

file_mode="$(
    stat -f '%Lp' "$ENV_FILE" 2>/dev/null \
        || stat -c '%a' "$ENV_FILE" 2>/dev/null \
        || true
)"
[[ "$file_mode" == "600" ]] || fail \
    "Integration test environment file must have permission 600: $ENV_FILE"

while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line//[[:space:]]/}" ]] && continue
    [[ "$line" == \#* ]] && continue
    [[ "$line" == *=* ]] || fail "Invalid environment file line."

    key="${line%%=*}"
    value="${line#*=}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail \
        "Invalid environment variable name in environment file."

    case "$key" in
        DABAEUM_SSH_USERNAME|DABAEUM_SSH_PASSWORD|DABAEUM_DB_USERNAME|DABAEUM_DB_PASSWORD \
            |DABAEUM_JWT_SIGNING_KEY|DABAEUM_DADAEGU_SITE_ID|DABAEUM_DADAEGU_PRIVATE_KEY \
            |DABAEUM_SSH_KNOWN_HOSTS)
            ;;
        *)
            fail "Unsupported environment variable in environment file: $key"
            ;;
    esac

    if [[ ${#value} -ge 2 \
        && ${value:0:1} == "'" \
        && ${value: -1} == "'" ]]; then
        value="${value:1:${#value}-2}"
    elif [[ ${#value} -ge 2 \
        && ${value:0:1} == '"' \
        && ${value: -1} == '"' ]]; then
        value="${value:1:${#value}-2}"
    fi

    export "$key=$value"
done < "$ENV_FILE"

# local 프로필과 같은 이름이다 (local-secrets.env.example). 통합 테스트는 local 프로필로 전체 컨텍스트를 올린다.
for required in DABAEUM_SSH_USERNAME DABAEUM_SSH_PASSWORD DABAEUM_DB_USERNAME DABAEUM_DB_PASSWORD \
    DABAEUM_JWT_SIGNING_KEY DABAEUM_DADAEGU_SITE_ID DABAEUM_DADAEGU_PRIVATE_KEY; do
    [[ -n "${!required:-}" ]] || fail "$required is required in environment file"
done

"$ROOT_DIR/scripts/verify-remote-db-test-env.sh"
exec "$ROOT_DIR/gradlew" --no-daemon integrationTest "$@"
