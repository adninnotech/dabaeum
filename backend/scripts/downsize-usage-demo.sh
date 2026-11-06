#!/usr/bin/env bash
# 현장검사용: 가상 데이터를 오픈 두 달 반 규모(기관 6·강좌 60·회원 650)로 줄이거나 되돌린다
# (docs/specs/usage-statistics.md §12 결정 15). dev 서버의 dabaeum_dev 전용.
#
#   downsize-usage-demo.sh [--dry-run]   줄이기(--dry-run 은 결과를 보여 주고 되돌린다)
#   downsize-usage-demo.sh --restore     원래대로(이름·나이·가입일은 rename-usage-demo-names.sh --restore 가 따로 되돌린다)
#
# 줄인 뒤에는 generate-usage-demo-data.sh --from 2026-08-17 --to <어제> 로 더미를 다시 넣는다.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/usage-demo/psql-dev.sh"

SQL_FILE=downsize.sql; END=COMMIT
case "${1:-}" in
  "") ;;
  --dry-run) END=ROLLBACK ;;
  --restore) SQL_FILE=restore-downsize.sql ;;
  *) echo "usage: $0 [--dry-run | --restore]" >&2; exit 1 ;;
esac

psql_dev <<SQL
BEGIN;
\i $DIR/usage-demo/$SQL_FILE
\i $DIR/usage-demo/downsize-summary.sql
$END;
SQL
