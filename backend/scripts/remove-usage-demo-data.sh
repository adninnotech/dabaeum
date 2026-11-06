#!/usr/bin/env bash
# 이용 통계 시연용 더미 데이터 지우기 (docs/specs/usage-statistics.md §12). dev 서버의 dabaeum_dev 전용.
# 'demo' 익명 키 행을 모두 지우고 그 날짜부터 다시 집계하게 한다. 실제 오픈 전에 cron 을 먼저 끄고 돌린다.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/usage-demo/psql-dev.sh"

psql_dev <<SQL
BEGIN;
\i $DIR/usage-demo/remove.sql
SELECT 'reaggregate from' AS what, f FROM demo_removed_from;
COMMIT;
SQL
