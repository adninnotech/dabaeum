# 시연용 더미 스크립트가 같이 쓰는 dabaeum_dev 접속. source 해서 쓴다. 표준입력의 SQL 을 한 번에 돌린다.
# DB 계정은 ~/dabaeum/dev-secrets.env(600)의 DABAEUM_DB_USERNAME·DABAEUM_DB_PASSWORD(DABAEUM_DEMO_ENV_FILE 로 바꿀 수 있다).
psql_dev() {
  local env_file=${DABAEUM_DEMO_ENV_FILE:-$HOME/dabaeum/dev-secrets.env}
  [ -f "$env_file" ] || { echo "env file not found: $env_file" >&2; return 1; }
  [ "$(stat -c '%a' "$env_file" 2>/dev/null || stat -f '%Lp' "$env_file")" = 600 ] \
    || { echo "env file must be 600: $env_file" >&2; return 1; }
  (
    set -a; . "$env_file"; set +a
    export PGPASSWORD="${DABAEUM_DB_PASSWORD:?missing in env file}"
    psql -h 127.0.0.1 -p 15432 -U "${DABAEUM_DB_USERNAME:?missing in env file}" -d dabaeum_dev \
      -v ON_ERROR_STOP=1 -q -P pager=off
  )
}
