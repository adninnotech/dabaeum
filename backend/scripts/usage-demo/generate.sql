-- 이용 통계 시연용 더미 데이터 생성 (docs/specs/usage-statistics.md §12).
-- dabaeum_dev 전용. 호출 쪽이 먼저 임시 테이블 demo_params(f date, t date) 를 만든다. 비어 있으면
-- t = 어제(서울), f = 마지막 더미 날짜 다음 날(없으면 t). 같은 기간을 다시 돌리면 그 기간 더미를 지우고 새로 넣는다.
-- 더미 행은 익명 키가 'demo' 로 시작한다(실제 키는 16진수라 겹치지 않는다). 지울 때는 remove.sql.
-- 넣은 날부터 집계 완료 기록을 지워 매시 20분 집계 배치가 그 날짜들을 다시 집계하게 한다.
-- 시연 기준 오픈일(2026-08-17) 이전은 더미를 넣지 않고, 이용 통계 집계에서도 지운다(§12 결정 7).

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'usage demo data is for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

DROP TABLE IF EXISTS demo_shift, demo_range, demo_days, demo_learners, demo_courses, demo_keywords, demo_kw_day, demo_banner_day;

CREATE TEMP TABLE demo_range AS
SELECT GREATEST(COALESCE(p.f,
                         (SELECT MAX(event_date) + 1 FROM tb_usage_events WHERE viewer_key LIKE 'demo%'),
                         COALESCE(p.t, (now() AT TIME ZONE 'Asia/Seoul')::date - 1)),
                o.launch) AS f,
       COALESCE(p.t, (now() AT TIME ZONE 'Asia/Seoul')::date - 1) AS t,
       o.launch
  FROM demo_params p, (SELECT DATE '2026-08-17' AS launch) o;

-- 오픈일 이전: 더미를 지우고, 이용 통계 집계(가상 신청·조회가 만든 숫자)도 지운다. 집계 완료 기록은 남겨
-- 배치가 다시 채우지 않게 한다. 되살리려면 tb_usage_aggregated_days 를 비우면 배치가 366일을 다시 쓴다.
DELETE FROM tb_usage_events e USING demo_range r WHERE e.viewer_key LIKE 'demo%' AND e.event_date < r.launch;
DELETE FROM tb_course_views v USING demo_range r WHERE v.viewer_key LIKE 'demo%' AND v.view_date < r.launch;
DELETE FROM tb_usage_daily_stats s USING demo_range r WHERE s.stat_date < r.launch;
DELETE FROM tb_search_keyword_daily k USING demo_range r WHERE k.stat_date < r.launch;

DELETE FROM tb_usage_events e USING demo_range r
 WHERE e.viewer_key LIKE 'demo%' AND e.event_date BETWEEN r.f AND r.t;
DELETE FROM tb_course_views v USING demo_range r
 WHERE v.viewer_key LIKE 'demo%' AND v.view_date BETWEEN r.f AND r.t;

-- 하루 규모: 평일 방문자 약 200명(회원 650명 규모, §12 결정 15) × 오픈 초기 증가(첫날 35% → 6주 뒤 100%) × 학기 × 주말 × 무작위 흔들림.
-- 그날 실제 신청이 몰렸으면(가상 신청이 하루 수백 건인 날) 방문자를 신청의 1.25~1.5배 이상으로만 올린다.
-- 추이 그래프에서 신청이 방문자를 넘지 않게 하는 최소한의 보정이다(크게 올리면 방문 그래프가 튄다. §12 결정 10).
CREATE TEMP TABLE demo_days AS
WITH base AS (
    SELECT d::date AS d,
           LEAST(0.35 + 0.65 * (d::date - r.launch) / 42.0, 1.0) AS growth,
           CASE EXTRACT(MONTH FROM d)::int
               WHEN 1 THEN 0.86 WHEN 2 THEN 0.80 WHEN 3 THEN 1.22 WHEN 4 THEN 1.10 WHEN 5 THEN 1.02
               WHEN 6 THEN 0.95 WHEN 7 THEN 0.90 WHEN 8 THEN 0.93 WHEN 9 THEN 1.17 WHEN 10 THEN 1.06
               WHEN 11 THEN 1.00 ELSE 0.94 END AS season,
           CASE WHEN EXTRACT(ISODOW FROM d) IN (6, 7) THEN 0.55 + random() * 0.10 ELSE 1.0 END AS weekday,
           0.85 + random() * 0.30 AS noise
      FROM demo_range r, generate_series(r.f, r.t, INTERVAL '1 day') AS g(d)
), enrolled AS (
    SELECT (e.applied_at AT TIME ZONE 'Asia/Seoul')::date AS d, COUNT(*) AS n
      FROM tb_enrollments e, demo_range r
     WHERE e.applied_at >= (r.f::timestamp AT TIME ZONE 'Asia/Seoul')
       AND e.applied_at < ((r.t + 1)::timestamp AT TIME ZONE 'Asia/Seoul')
     GROUP BY 1
), v AS (
    SELECT b.d, GREATEST(round(200 * b.growth * b.season * b.weekday * b.noise)::int,
                         round(COALESCE(en.n, 0) * (1.25 + random() * 0.25))::int, 1) AS visitors
      FROM base b LEFT JOIN enrolled en ON en.d = b.d
)
SELECT d, visitors,
       round(visitors * (0.38 + random() * 0.08))::int AS searches,
       round(visitors * (1.25 + random() * 0.25))::int AS views,
       round(visitors * (0.025 + random() * 0.015))::int AS clicks
  FROM v;

-- 회원 몫은 실제 학습자(수강 신청이 있는 활성 회원) 중에서 고르게 고른다. 회원 나이·성별 분포는 이름 바꾸기
-- 스크립트가 평생학습 이용자 기준으로 맞춘다(§12 결정 13). 무게 누적 구간 [lo, lo + w) 를 lo 색인으로 찾는다.
CREATE TEMP TABLE demo_learners AS
WITH w AS (
    SELECT u.id, u.gender, u.birth_date,
           1.0 AS w
      FROM tb_users u
     WHERE u.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM tb_enrollments e WHERE e.user_id = u.id)
)
SELECT id, gender, birth_date, SUM(w) OVER (ORDER BY id) - w AS lo FROM w;
CREATE INDEX ON demo_learners (lo);
ANALYZE demo_learners;

-- 조회 강좌는 공개 강좌를 실제 신청 수(+1)에 비례해 고른다. 인기 강좌 순위가 거의 그대로 남는다.
-- dev DB 는 2026-10-12 에 다시 만들어 강좌·배너의 생성일이 최근이다. 신청 기록은 그 전 날짜로 들어 있으므로
-- 생성일로 거르지 않는다(통계 화면은 사이트 전체 숫자라 강좌별 시작일이 드러나지 않는다).
CREATE TEMP TABLE demo_courses AS
SELECT c.id,
       (SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id) + 1 AS weight
  FROM tb_courses c
 WHERE c.deleted_at IS NULL AND c.status NOT IN ('DRAFT', 'CANCELLED');

-- 검색어 후보: 무게(순위)와 많이 찾는 달. 결과 0건 여부는 통합검색과 같은 조건(LOWER LIKE)으로 그때그때 따진다.
-- 결과가 없는 검색어는 무게를 크게 줄여 인기 순위 아래쪽·결과 0건 표에만 나오게 한다.
CREATE TEMP TABLE demo_keywords AS
SELECT k.keyword, k.weight, k.months,
       NOT (EXISTS (SELECT 1 FROM tb_courses c WHERE c.deleted_at IS NULL AND c.status NOT IN ('DRAFT', 'CANCELLED')
                       AND LOWER(c.title) LIKE '%' || k.keyword || '%')
            OR EXISTS (SELECT 1 FROM tb_institutions i WHERE i.deleted_at IS NULL AND i.status = 'ACTIVE'
                       AND LOWER(i.name) LIKE '%' || k.keyword || '%')
            OR EXISTS (SELECT 1 FROM tb_notices n WHERE n.status = 'PUBLISHED' AND n.audience = 'PUBLIC'
                       AND LOWER(n.title) LIKE '%' || k.keyword || '%')
            OR EXISTS (SELECT 1 FROM tb_faqs f WHERE LOWER(f.question) LIKE '%' || k.keyword || '%')) AS zero_result
  FROM (VALUES
    ('엑셀', 10.0, ARRAY[1, 2, 3]), ('컴퓨터', 8.0, ARRAY[1, 2]), ('요가', 8.0, ARRAY[3, 4, 9]),
    ('파이썬', 7.0, ARRAY[1, 2, 7]), ('영어', 7.0, ARRAY[1, 3]), ('자격증', 7.0, ARRAY[1, 2]),
    ('캘리그라피', 6.0, ARRAY[11, 12]), ('수영', 6.0, ARRAY[6, 7, 8]), ('인공지능', 5.5, ARRAY[9, 10]),
    ('스마트폰', 5.5, ARRAY[3, 4]), ('필라테스', 5.0, ARRAY[3, 9]), ('일본어', 5.0, ARRAY[1, 7]),
    ('사진', 5.0, ARRAY[4, 5, 10]), ('요리', 5.0, ARRAY[11, 12]), ('코딩', 4.5, ARRAY[7, 8]),
    ('영상편집', 4.5, ARRAY[1, 2]), ('유튜브', 4.0, ARRAY[1, 2]), ('중국어', 4.0, ARRAY[3]),
    ('드로잉', 4.0, ARRAY[5, 6]), ('한국사', 4.0, ARRAY[1, 2]), ('제과제빵', 3.5, ARRAY[11, 12]),
    ('바리스타', 3.5, ARRAY[1, 2]), ('포토샵', 3.5, ARRAY[1, 2]), ('미술', 3.0, ARRAY[4, 5]),
    ('피아노', 3.0, ARRAY[3]), ('서예', 3.0, ARRAY[11]), ('독서', 3.0, ARRAY[9, 10]),
    ('글쓰기', 3.0, ARRAY[9, 10]), ('인문학', 3.0, ARRAY[9, 10]), ('심리', 3.0, ARRAY[3, 4]),
    ('창업', 3.0, ARRAY[1, 2]), ('재테크', 3.0, ARRAY[1]), ('블로그', 2.5, ARRAY[1, 2]),
    ('꽃꽂이', 2.5, ARRAY[5]), ('우쿨렐레', 2.5, ARRAY[3, 4]), ('부동산', 2.5, ARRAY[1, 2]),
    ('등산', 2.0, ARRAY[4, 5, 10]), ('원예', 2.0, ARRAY[4, 5]), ('도자기', 2.0, ARRAY[5, 6]),
    ('목공', 2.0, ARRAY[6]), ('가죽공예', 2.0, ARRAY[11, 12]), ('합창', 2.0, ARRAY[9]),
    ('부모교육', 2.0, ARRAY[3]), ('드론', 2.0, ARRAY[4, 5]), ('반려동물', 2.0, ARRAY[5, 6]),
    ('주식', 1.8, ARRAY[1]), ('상담', 1.8, ARRAY[3]), ('건강', 1.8, ARRAY[1, 3])
  ) AS k(keyword, weight, months);

-- 날마다 검색어 무게를 흔들어 순위가 조금씩 바뀌게 하고, 누적 구간 [lo, hi) 로 고른다.
CREATE TEMP TABLE demo_kw_day AS
WITH w AS (
    SELECT dd.d, k.keyword, k.zero_result,
           k.weight * CASE WHEN k.zero_result THEN 0.18 ELSE 1 END
                    * CASE WHEN EXTRACT(MONTH FROM dd.d)::int = ANY (k.months) THEN 2.1 ELSE 1 END
                    * (0.75 + random() * 0.5) AS w
      FROM demo_days dd CROSS JOIN demo_keywords k
)
SELECT d, keyword, zero_result,
       SUM(w) OVER (PARTITION BY d ORDER BY keyword) - w AS lo,
       SUM(w) OVER (PARTITION BY d ORDER BY keyword) AS hi,
       SUM(w) OVER (PARTITION BY d) AS total
  FROM w;

-- 클릭 배너: 지금 지우지 않은 배너 중 공개이거나 그날이 노출 기간 안인 것(생성일로 거르지 않는 이유는 강좌와 같다)
CREATE TEMP TABLE demo_banner_day AS
SELECT dd.d, b.id, row_number() OVER (PARTITION BY dd.d ORDER BY b.id) AS rn,
       COUNT(*) OVER (PARTITION BY dd.d) AS n
  FROM demo_days dd
  JOIN tb_banners b ON b.deleted_at IS NULL
                   AND (b.visibility = 'PUBLIC'
                        OR (b.visibility = 'SCHEDULED' AND dd.d BETWEEN b.start_at::date AND b.end_at::date));

-- 사람 한 명 = 회원(45%, 실제 학습자의 성별·그날 기준 연령대) 또는 비회원.
-- 아래 세 INSERT 가 같은 규칙을 쓴다. r 을 바깥 행에서 받아 회원을 행마다 따로 고른다.
INSERT INTO tb_usage_events (event_date, event_type, viewer_key, target_key, member_type, gender, age_band,
                             zero_result, created_at)
SELECT s.d, 'VISIT', 'demo' || substr(md5(s.d || ':v:' || s.i) || md5(s.d || ':v:' || s.i || ':2'), 1, 60), '',
       CASE WHEN p.id IS NULL THEN 'GUEST' ELSE 'MEMBER' END,
       CASE WHEN p.id IS NULL THEN 'UNKNOWN' ELSE COALESCE(p.gender, 'UNKNOWN') END,
       CASE WHEN p.birth_date IS NULL THEN 'UNKNOWN'
            ELSE LEAST(GREATEST((EXTRACT(YEAR FROM age(s.d, p.birth_date))::int / 10) * 10, 10), 70)::text END,
       false, (s.d + (7 + random() * 16) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Seoul'
  FROM (SELECT dd.d, g.i, random() < 0.45 AS member, random() AS r
          FROM demo_days dd CROSS JOIN LATERAL generate_series(1, dd.visitors) AS g(i)) s
  LEFT JOIN LATERAL (SELECT l.* FROM demo_learners l
                      WHERE s.member AND l.lo <= s.r * (SELECT MAX(lo) FROM demo_learners)
                      ORDER BY l.lo DESC LIMIT 1) p ON true;

INSERT INTO tb_usage_events (event_date, event_type, viewer_key, target_key, member_type, gender, age_band,
                             zero_result, created_at)
SELECT s.d, 'SEARCH', 'demo' || substr(md5(s.d || ':s:' || s.i) || md5(s.d || ':s:' || s.i || ':2'), 1, 60), k.keyword,
       CASE WHEN p.id IS NULL THEN 'GUEST' ELSE 'MEMBER' END,
       CASE WHEN p.id IS NULL THEN 'UNKNOWN' ELSE COALESCE(p.gender, 'UNKNOWN') END,
       CASE WHEN p.birth_date IS NULL THEN 'UNKNOWN'
            ELSE LEAST(GREATEST((EXTRACT(YEAR FROM age(s.d, p.birth_date))::int / 10) * 10, 10), 70)::text END,
       k.zero_result, (s.d + (7 + random() * 16) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Seoul'
  FROM (SELECT dd.d, g.i, random() < 0.45 AS member, random() AS r, random() AS pick
          FROM demo_days dd CROSS JOIN LATERAL generate_series(1, dd.searches) AS g(i)) s
  JOIN demo_kw_day k ON k.d = s.d AND s.pick * k.total >= k.lo AND s.pick * k.total < k.hi
  LEFT JOIN LATERAL (SELECT l.* FROM demo_learners l
                      WHERE s.member AND l.lo <= s.r * (SELECT MAX(lo) FROM demo_learners)
                      ORDER BY l.lo DESC LIMIT 1) p ON true;

INSERT INTO tb_usage_events (event_date, event_type, viewer_key, target_key, member_type, gender, age_band,
                             zero_result, created_at)
SELECT s.d, 'BANNER_CLICK', 'demo' || substr(md5(s.d || ':b:' || s.i) || md5(s.d || ':b:' || s.i || ':2'), 1, 60), bd.id::text,
       CASE WHEN p.id IS NULL THEN 'GUEST' ELSE 'MEMBER' END,
       CASE WHEN p.id IS NULL THEN 'UNKNOWN' ELSE COALESCE(p.gender, 'UNKNOWN') END,
       CASE WHEN p.birth_date IS NULL THEN 'UNKNOWN'
            ELSE LEAST(GREATEST((EXTRACT(YEAR FROM age(s.d, p.birth_date))::int / 10) * 10, 10), 70)::text END,
       false, (s.d + (7 + random() * 16) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Seoul'
  FROM (SELECT dd.d, g.i, random() < 0.45 AS member, random() AS r, random() AS pick
          FROM demo_days dd CROSS JOIN LATERAL generate_series(1, dd.clicks) AS g(i)) s
  JOIN demo_banner_day bd ON bd.d = s.d AND bd.rn = 1 + floor(s.pick * bd.n)::int
  LEFT JOIN LATERAL (SELECT l.* FROM demo_learners l
                      WHERE s.member AND l.lo <= s.r * (SELECT MAX(lo) FROM demo_learners)
                      ORDER BY l.lo DESC LIMIT 1) p ON true;

-- 강좌 조회: 그날 있던 강좌마다 (그날 조회 수 × 강좌 무게 비율 × 흔들림) 건을 만든다.
-- 회원이면 그 학습자 id 를 남긴다(기존 조회 기록과 같은 형태).
INSERT INTO tb_course_views (course_id, viewer_key, view_date, user_id, viewed_at)
SELECT s.id, 'demo' || substr(md5(s.d || ':c:' || s.id || ':' || s.i) || md5(s.d || ':c:' || s.id || ':' || s.i || ':2'), 1, 60), s.d,
       CASE WHEN s.member THEN p.id END,
       (s.d + (7 + random() * 16) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Seoul'
  FROM (SELECT cd.d, cd.id, g.i, random() < 0.45 AS member, random() AS r
          FROM (SELECT dd.d, dc.id,
                       floor(dd.views * dc.weight::numeric / SUM(dc.weight) OVER (PARTITION BY dd.d) * (0.7 + random() * 0.6)
                             + random())::int AS n
                  FROM demo_days dd CROSS JOIN demo_courses dc) cd
          CROSS JOIN LATERAL generate_series(1, cd.n) AS g(i)) s
  LEFT JOIN LATERAL (SELECT l.id FROM demo_learners l
                      WHERE s.member AND l.lo <= s.r * (SELECT MAX(lo) FROM demo_learners)
                      ORDER BY l.lo DESC LIMIT 1) p ON true;

-- 모집 중 강좌 일정 밀기 (§12 결정 17). 강좌 상태를 날짜로 바꾸는 작업이 없어, 현장검사(2026-11-11~12) 전에 모집이 끝나거나
-- 개강하는 강좌가 "모집 중"으로 남는다. 모집 마감이 2026-11-14 전이면 모집 마감·개강·종료·회차를 11-14~11-28 마감이 되도록 함께 민다.
-- 원래 값은 demo_course_date_backup 에 처음 한 번만 남기고 restore-downsize.sql 이 되돌린다. 한 번 밀면 조건에 안 걸려 다시 밀지 않는다.
CREATE TABLE IF NOT EXISTS demo_course_date_backup (
    tbl TEXT  NOT NULL,
    id  UUID  NOT NULL,
    row JSONB NOT NULL,
    PRIMARY KEY (tbl, id)
);
CREATE TEMP TABLE demo_shift ON COMMIT DROP AS
SELECT c.id, (DATE '2026-11-14' - c.recruit_end_date) + floor(random() * 15)::int AS days
  FROM tb_courses c
 WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL AND c.status = 'RECRUITING'
   AND c.recruit_end_date < DATE '2026-11-14';
INSERT INTO demo_course_date_backup (tbl, id, row)
SELECT 'tb_courses', c.id, to_jsonb(c) FROM tb_courses c JOIN demo_shift d ON d.id = c.id ON CONFLICT DO NOTHING;
INSERT INTO demo_course_date_backup (tbl, id, row)
SELECT 'tb_course_sessions', x.id, to_jsonb(x) FROM tb_course_sessions x JOIN demo_shift d ON d.id = x.course_id ON CONFLICT DO NOTHING;
UPDATE tb_courses c
   SET recruit_end_date = c.recruit_end_date + d.days, start_date = c.start_date + d.days, end_date = c.end_date + d.days
  FROM demo_shift d WHERE c.id = d.id;
UPDATE tb_course_sessions x
   SET starts_at = x.starts_at + d.days * INTERVAL '1 day', ends_at = x.ends_at + d.days * INTERVAL '1 day',
       attendance_opens_at = x.attendance_opens_at + d.days * INTERVAL '1 day',
       attendance_closes_at = x.attendance_closes_at + d.days * INTERVAL '1 day'
  FROM demo_shift d WHERE x.course_id = d.id;

-- 수강 신청 이어 넣기 (§12 결정 16). 가상 신청은 10월에서 끝나 그 뒤가 0건이므로, 2026-10-27 부터 하루 2~6건(주말 1~3건)을
-- 넣는다. 모집 기간 안이고 정원이 남은 가상 강좌에, 그날 이미 가입했고 그 강좌에 신청이 없는 가상 회원이 신청하는 형태다.
-- 가상 강좌는 선착순이라 모두 승인으로 넣는다. 넣은 신청 id 는 demo_added_enrollments 에 남겨 다시 돌리거나 지울 때 이것만 지운다.
CREATE TABLE IF NOT EXISTS demo_added_enrollments (
    id       UUID NOT NULL PRIMARY KEY,
    added_on DATE NOT NULL
);

DELETE FROM tb_enrollments e USING demo_added_enrollments a, demo_range r
 WHERE e.id = a.id AND a.added_on BETWEEN r.f AND r.t;
DELETE FROM demo_added_enrollments a USING demo_range r WHERE a.added_on BETWEEN r.f AND r.t;

DO $$
DECLARE
    day DATE;
    want INT;
BEGIN
    FOR day IN SELECT g.d::date FROM demo_range r, generate_series(GREATEST(r.f, DATE '2026-10-27'), r.t, INTERVAL '1 day') AS g(d) LOOP
        want := CASE WHEN EXTRACT(ISODOW FROM day) IN (6, 7) THEN 1 + floor(random() * 3) ELSE 2 + floor(random() * 5) END;
        WITH picked AS (
            SELECT c.id AS course_id, u.id AS user_id, random() AS r
              FROM tb_courses c
              JOIN tb_users u ON u.email LIKE '%@synthetic.invalid' AND u.status = 'ACTIVE'
                             AND (u.created_at AT TIME ZONE 'Asia/Seoul')::date <= day
             WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL AND c.status = 'RECRUITING'
               AND day BETWEEN c.recruit_start_date AND c.recruit_end_date
               AND (SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status IN ('APPROVED', 'APPLIED')) < c.capacity
               AND NOT EXISTS (SELECT 1 FROM tb_enrollments e WHERE e.course_id = c.id AND e.user_id = u.id)
             ORDER BY random() LIMIT 400
        ), one_per_course AS (
            SELECT DISTINCT ON (course_id) course_id, user_id, r FROM picked ORDER BY course_id, r
        ), chosen AS (
            SELECT course_id, user_id, gen_random_uuid() AS id,
                   (day + (8 + random() * 14) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Seoul' AS at
              FROM one_per_course ORDER BY random() LIMIT want
        ), inserted AS (
            INSERT INTO tb_enrollments (id, course_id, user_id, status, applied_at, approved_at, created_at, updated_at)
            SELECT id, course_id, user_id, 'APPROVED', at, at + INTERVAL '2 hours', at, at
              FROM chosen
            RETURNING id
        )
        INSERT INTO demo_added_enrollments (id, added_on) SELECT id, day FROM inserted;
    END LOOP;
END $$;

DELETE FROM tb_usage_aggregated_days a USING demo_range r WHERE a.stat_date >= r.f;
