#!/usr/bin/env bash
# 이용 통계 시연용 가상 회원에게 관심 분야 넣기 (docs/specs/interest-based-member-profile.md §4). dev 서버의 dabaeum_dev 전용.
# 가상 회원(@synthetic.invalid) 약 60% 에게 관심 분야 1~3개와 INTEREST 동의 기록을 넣는다. 다시 돌려도 중복되지 않는다.
#
#   add-usage-demo-interests.sh [--dry-run]   넣기(--dry-run 은 결과를 보여 주고 되돌린다)
#
# 지우기는 remove-usage-demo-data.sh 가 함께 한다. 추천 모델 학습에는 가상 데이터라 성능 수치가 부풀 수 있다.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/usage-demo/psql-dev.sh"

END=COMMIT
case "${1:-}" in
  "") ;;
  --dry-run) END=ROLLBACK ;;
  *) echo "usage: $0 [--dry-run]" >&2; exit 1 ;;
esac

psql_dev <<SQL
BEGIN;
\i $DIR/usage-demo/add-interests.sql
SELECT (SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid') AS members,
       COUNT(DISTINCT i.user_id) AS members_with_interests, COUNT(*) AS interests
  FROM tb_user_interests i JOIN tb_users u ON u.id = i.user_id WHERE u.email LIKE '%@synthetic.invalid';
$END;
SQL
echo "$(date '+%F %T') usage demo interests: done ($END)"
