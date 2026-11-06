#!/usr/bin/env bash
# 시연용: 가상 데이터(코드 SYN-)의 강좌·기관 이름을 운영처럼 보이는 이름으로 바꾼다 (docs/specs/usage-statistics.md §12).
# dev 서버의 dabaeum_dev 전용. 원래 이름은 DB 의 demo_name_backup 에 남는다.
#
#   rename-usage-demo-names.sh [--dry-run]     바꾸기(--dry-run 은 바꾼 결과 일부를 보여 주고 되돌린다)
#   rename-usage-demo-names.sh --restore       원래 이름으로 되돌리기
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/usage-demo/psql-dev.sh"

SQL_FILE=rename-names.sql; END=COMMIT
case "${1:-}" in
  "") ;;
  --dry-run) END=ROLLBACK ;;
  --restore) SQL_FILE=restore-names.sql ;;
  *) echo "unknown option: $1" >&2; exit 1 ;;
esac

psql_dev <<SQL
BEGIN;
\i $DIR/usage-demo/$SQL_FILE
SELECT course_code, title FROM tb_courses WHERE course_code LIKE 'SYN-%' ORDER BY course_code LIMIT 12;
SELECT institution_code, name FROM tb_institutions WHERE institution_code LIKE 'SYN-%' ORDER BY institution_code;
SELECT LEAST(GREATEST((EXTRACT(YEAR FROM age(birth_date))::int / 10) * 10, 10), 70) AS age_band, COUNT(*) AS members,
       COUNT(*) FILTER (WHERE gender = 'FEMALE') AS female, COUNT(*) FILTER (WHERE gender = 'MALE') AS male
  FROM tb_users WHERE email LIKE '%@synthetic.invalid' GROUP BY 1 ORDER BY 1;
SELECT EXTRACT(YEAR FROM birth_date)::int AS born, gender, name FROM tb_users
 WHERE email LIKE '%@synthetic.invalid' ORDER BY random() LIMIT 12;
SELECT to_char(created_at AT TIME ZONE 'Asia/Seoul', 'IYYY-"W"IW') AS week, COUNT(*) AS synthetic_signups
  FROM tb_users WHERE email LIKE '%@synthetic.invalid' GROUP BY 1 ORDER BY 1;
$END;
SQL
