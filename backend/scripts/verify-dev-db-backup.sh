#!/usr/bin/env bash
# 가장 최근 정기 백업의 복구 시험. 임시 DB 에 복원해 주요 테이블 건수를 dabaeum_dev 와 비교하고 임시 DB 를 지운다.
# dabaeum_dev 에는 읽기만 한다. 복구 시험 결과는 ~/dabaeum/backup/restore-check.log 에 남긴다.
set -euo pipefail

ENV_FILE=${DABAEUM_BACKUP_ENV_FILE:-$HOME/dabaeum/dev-secrets.env}
BACKUP_DIR=${DABAEUM_BACKUP_DIR:-$HOME/dabaeum/backup}
CHECK_DB=dabaeum_restore_check

[ "$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE")" = 600 ] || { echo "env file must be 600: $ENV_FILE" >&2; exit 1; }
set -a; . "$ENV_FILE"; set +a
export PGPASSWORD="${DABAEUM_DB_PASSWORD:?missing in env file}"
PG=(-h 127.0.0.1 -p 15432 -U "${DABAEUM_DB_USERNAME:?missing in env file}")

DUMP=$(ls -t "$BACKUP_DIR"/dabaeum_dev-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9].dump 2>/dev/null | head -1)
[ -n "$DUMP" ] || { echo "no backup found in $BACKUP_DIR" >&2; exit 1; }

trap 'dropdb "${PG[@]}" --if-exists "$CHECK_DB"' EXIT
dropdb "${PG[@]}" --if-exists "$CHECK_DB"
createdb "${PG[@]}" "$CHECK_DB"
START=$(date +%s)
pg_restore "${PG[@]}" -d "$CHECK_DB" --no-owner --no-privileges "$DUMP"
SECONDS_TAKEN=$(( $(date +%s) - START ))

COUNTS="SELECT (SELECT COUNT(*) FROM tb_users), (SELECT COUNT(*) FROM tb_courses), (SELECT COUNT(*) FROM tb_enrollments), (SELECT COUNT(*) FROM tb_usage_daily_stats), (SELECT COUNT(*) FROM flyway_schema_history)"
RESTORED=$(psql "${PG[@]}" -d "$CHECK_DB" -Atc "$COUNTS")
SOURCE=$(psql "${PG[@]}" -d dabaeum_dev -Atc "$COUNTS")

RESULT="$(date '+%F %T') restore check: dump=$(basename "$DUMP") seconds=$SECONDS_TAKEN restored=$RESTORED source=$SOURCE (users|courses|enrollments|usage_stats|migrations)"
echo "$RESULT" | tee -a "$BACKUP_DIR/restore-check.log"
