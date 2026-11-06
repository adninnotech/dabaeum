-- 가상 회원 약 60% 에게 관심 분야 1~3개와 동의 기록을 넣는다 (docs/specs/interest-based-member-profile.md §4). dabaeum_dev 전용.
-- 관심 분야는 그 회원이 신청한 강좌의 대분류에서 주로(약 75%) 뽑고 나머지는 무작위 대분류로 섞는다(수치가 지나치게 맞지 않게).
-- 해시로 정해서 다시 돌려도 같은 회원·같은 분야이고, 이미 관심 분야가 있는 회원은 건드리지 않는다(중복 없음).
-- 지우기는 remove.sql. 트랜잭션은 호출하는 셸(add-usage-demo-interests.sh)이 연다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'usage demo interests are for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

-- 같은 입력이면 같은 0 이상 정수(28비트)를 돌려주는 결정적 난수
CREATE OR REPLACE FUNCTION pg_temp.demo_h(t TEXT) RETURNS INT
    LANGUAGE sql IMMUTABLE AS $$ SELECT ('x' || substr(md5(t), 1, 7))::bit(28)::int $$;

DROP TABLE IF EXISTS demo_interest_rows;
CREATE TEMP TABLE demo_interest_rows AS
WITH sel AS (
    SELECT u.id, u.created_at, 1 + pg_temp.demo_h(u.id::text || 'n') % 3 AS n
      FROM tb_users u
     WHERE u.email LIKE '%@synthetic.invalid'
       AND pg_temp.demo_h(u.id::text || 'sel') % 100 < 60
       AND NOT EXISTS (SELECT 1 FROM tb_user_interests i WHERE i.user_id = u.id)),
enrolled AS (
    SELECT s.id AS user_id, left(c.classification_code, 7) || '0000' AS code, COUNT(*) AS cnt
      FROM sel s
      JOIN tb_enrollments e ON e.user_id = s.id
      JOIN tb_courses c ON c.id = e.course_id
     WHERE c.classification_code IS NOT NULL
     GROUP BY 1, 2),
ranked AS (
    SELECT user_id, code,
           row_number() OVER (PARTITION BY user_id ORDER BY cnt DESC, pg_temp.demo_h(user_id::text || code)) AS rn
      FROM enrolled),
slots AS (
    SELECT s.id AS user_id, s.created_at, g AS slot
      FROM sel s CROSS JOIN LATERAL generate_series(1, s.n) AS g)
SELECT sl.user_id, sl.created_at + INTERVAL '10 minutes' * sl.slot AS created_at,
       COALESCE(CASE WHEN pg_temp.demo_h(sl.user_id::text || sl.slot::text || 'mix') % 100 < 75 THEN r.code END,
                rnd.code) AS code
  FROM slots sl
  LEFT JOIN ranked r ON r.user_id = sl.user_id AND r.rn = sl.slot
  CROSS JOIN LATERAL (SELECT k.code FROM tb_course_classifications k
                       WHERE k.level = 1
                       ORDER BY pg_temp.demo_h(sl.user_id::text || sl.slot::text || k.code) LIMIT 1) rnd;

INSERT INTO tb_user_interests (user_id, classification_code, created_at)
SELECT user_id, code, MIN(created_at) FROM demo_interest_rows GROUP BY user_id, code
ON CONFLICT (user_id, classification_code) DO NOTHING;

INSERT INTO tb_user_agreements (id, user_id, agreement_type, agreed, agreed_at)
SELECT gen_random_uuid(), user_id, 'INTEREST', TRUE, MIN(created_at)
  FROM demo_interest_rows r
 WHERE NOT EXISTS (SELECT 1 FROM tb_user_agreements a WHERE a.user_id = r.user_id AND a.agreement_type = 'INTEREST')
 GROUP BY user_id;
