#!/usr/bin/env bash
# 시연 중에만 QA 시험 강좌를 공개 목록에서 빼고 되돌린다 (docs/specs/usage-statistics.md §12 결정 12). dabaeum_dev 전용.
#
#   toggle-demo-qa-courses.sh --hide [--dry-run]   시연 직전: 'QA…' 공개 강좌를 DRAFT 로(원래 상태 백업)
#   toggle-demo-qa-courses.sh --show               시연 직후: 원래 상태로 되돌리고 백업과 대조
#
# 바꾸기 전·되돌린 뒤 QA 세션에 알린다(그 사이 QA 는 시험을 돌리지 않는다). 기관은 건드리지 않는다.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/usage-demo/psql-dev.sh"

END=COMMIT
case "${1:-}" in
  --hide) SQL_FILE=hide-qa-courses.sql; [ "${2:-}" = --dry-run ] && END=ROLLBACK ;;
  --show) SQL_FILE=show-qa-courses.sql ;;
  *) echo "usage: $0 --hide [--dry-run] | --show" >&2; exit 1 ;;
esac

psql_dev <<SQL
BEGIN;
\i $DIR/usage-demo/$SQL_FILE
SELECT title, status FROM tb_courses WHERE course_code NOT LIKE 'SYN-%' AND title LIKE 'QA%' AND deleted_at IS NULL ORDER BY title;
$END;
SQL
