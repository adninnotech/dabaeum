#!/usr/bin/env bash
# dev DB(dabaeum_dev) 정기 백업. dev 서버 cron 이 매일 돌린다. dabaeum_dev 만 덤프한다.
# ~/dabaeum/backup/dabaeum_dev-YYYYMMDD.dump(600)로 남기고 14일 지난 정기 백업만 지운다. 다른 이름의 수동 덤프는 지우지 않는다.
# DB 계정은 ~/dabaeum/dev-secrets.env(600). 복구 시험은 verify-dev-db-backup.sh.
set -euo pipefail
umask 077

ENV_FILE=${DABAEUM_BACKUP_ENV_FILE:-$HOME/dabaeum/dev-secrets.env}
BACKUP_DIR=${DABAEUM_BACKUP_DIR:-$HOME/dabaeum/backup}
KEEP_DAYS=14

[ "$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE")" = 600 ] || { echo "env file must be 600: $ENV_FILE" >&2; exit 1; }
set -a; . "$ENV_FILE"; set +a
export PGPASSWORD="${DABAEUM_DB_PASSWORD:?missing in env file}"

mkdir -p "$BACKUP_DIR"
TARGET="$BACKUP_DIR/dabaeum_dev-$(date +%Y%m%d).dump"
TMP="$TARGET.tmp"
trap 'rm -f "$TMP"' EXIT

pg_dump -h 127.0.0.1 -p 15432 -U "${DABAEUM_DB_USERNAME:?missing in env file}" -d dabaeum_dev -Fc -f "$TMP"
pg_restore --list "$TMP" > /dev/null
mv "$TMP" "$TARGET"

find "$BACKUP_DIR" -maxdepth 1 -type f -name 'dabaeum_dev-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9].dump' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') backup ok: $TARGET $(du -h "$TARGET" | cut -f1)"
