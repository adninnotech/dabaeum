#!/usr/bin/env bash
# 이용 통계 시연용 더미 데이터 넣기 (docs/specs/usage-statistics.md §12). dev 서버의 dabaeum_dev 전용.
#
#   generate-usage-demo-data.sh --from 2025-11-01 --to 2026-10-29 [--dry-run]   기간 채우기(다시 돌리면 그 기간을 새로 씀)
#   generate-usage-demo-data.sh --daily [--dry-run]                             마지막 더미 다음 날부터 어제까지(cron 용)
#
# --dry-run 은 같은 SQL 을 돌린 뒤 미리보기를 찍고 되돌린다. 지우기는 remove-usage-demo-data.sh.
# DB 접속은 usage-demo/psql-dev.sh. 강좌·기관 이름은 rename-usage-demo-names.sh 가 따로 바꾼다.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/usage-demo/psql-dev.sh"
FROM=""; TO=""; DAILY=0; DRY_RUN=0

die() { echo "generate-usage-demo-data: $*" >&2; exit 1; }
is_date() { [[ $1 =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; }

while [ $# -gt 0 ]; do
  case "$1" in
    --from) FROM=${2:-}; shift 2 ;;
    --to) TO=${2:-}; shift 2 ;;
    --daily) DAILY=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    *) die "unknown option: $1" ;;
  esac
done

if [ "$DAILY" = 1 ]; then
  [ -z "$FROM$TO" ] || die "--daily takes no --from/--to"
  FROM_SQL=NULL; TO_SQL=NULL
else
  is_date "$FROM" && is_date "$TO" || die "--from and --to must be YYYY-MM-DD (or use --daily)"
  FROM_SQL="DATE '$FROM'"; TO_SQL="DATE '$TO'"
fi

END=COMMIT; [ "$DRY_RUN" = 1 ] && END=ROLLBACK
echo "$(date '+%F %T') usage demo: from=${FROM:-auto} to=${TO:-yesterday} dry_run=$DRY_RUN"
psql_dev <<SQL
BEGIN;
CREATE TEMP TABLE demo_params (f date, t date);
INSERT INTO demo_params VALUES ($FROM_SQL, $TO_SQL);
\i $DIR/usage-demo/generate.sql
SELECT 'range' AS what, f, t FROM demo_range;
$( [ "$DRY_RUN" = 1 ] && echo "\\i $DIR/usage-demo/summary.sql" )
$END;
SQL
echo "$(date '+%F %T') usage demo: done ($END)"
