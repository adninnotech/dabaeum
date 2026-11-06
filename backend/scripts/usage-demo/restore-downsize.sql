-- downsize.sql 로 줄인 가상 데이터를 원래대로 되돌린다 (docs/specs/usage-statistics.md §12 결정 15). dabaeum_dev 전용.
-- 지운 행은 부모부터 다시 넣고(회원 → 딸린 행 → 신청 → 출석·수료 → 찜), 바꾼 강좌·기관·신청은 백업 값으로 되돌린다.
-- 회원 복원은 jsonb_populate_record 로 하므로 없어진 컬럼(지역·학번)의 키는 무시한다. 컬럼 이름을 직접 쓰지 않아
-- 지역·학번 컬럼 삭제 마이그레이션 전후 모두 동작한다.
-- 주의: 삭제 전에 만든 백업을 삭제 전 스키마에 복원하면 학번 채번 트리거가 켜져 있어 학번이 새로 발급된다.
-- 삭제 마이그레이션 전에 복원해야 한다면 먼저 trg_tb_users_issue_learner_no 를 직접 끈다(이 스크립트는 끄지 않는다).
-- 이름·가입일·나이·성별은 이 다음에 rename-usage-demo-names.sh --restore 로 되돌린다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'demo downsize is for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS demo_downsize_backup (seq BIGSERIAL PRIMARY KEY, tbl TEXT NOT NULL, row JSONB NOT NULL);
CREATE TABLE IF NOT EXISTS demo_downsize_state (k TEXT PRIMARY KEY, at TIMESTAMPTZ NOT NULL DEFAULT now());
-- 이어 넣은 수강 신청이 남아 있으면 먼저 지운다(§12 결정 16). 보통은 remove-usage-demo-data.sh 가 먼저 지운다.
CREATE TABLE IF NOT EXISTS demo_added_enrollments (id UUID NOT NULL PRIMARY KEY, added_on DATE NOT NULL);
DELETE FROM tb_enrollments e USING demo_added_enrollments a WHERE e.id = a.id;
DROP TABLE demo_added_enrollments;
-- 밀어 둔 모집 중 강좌 일정을 원래대로 돌린다(§12 결정 17).
CREATE TABLE IF NOT EXISTS demo_course_date_backup (tbl TEXT NOT NULL, id UUID NOT NULL, row JSONB NOT NULL, PRIMARY KEY (tbl, id));
UPDATE tb_courses c
   SET recruit_end_date = (b.row ->> 'recruit_end_date')::date, start_date = (b.row ->> 'start_date')::date,
       end_date = (b.row ->> 'end_date')::date
  FROM demo_course_date_backup b WHERE b.tbl = 'tb_courses' AND b.id = c.id;
UPDATE tb_course_sessions x
   SET starts_at = (b.row ->> 'starts_at')::timestamptz, ends_at = (b.row ->> 'ends_at')::timestamptz,
       attendance_opens_at = (b.row ->> 'attendance_opens_at')::timestamptz,
       attendance_closes_at = (b.row ->> 'attendance_closes_at')::timestamptz
  FROM demo_course_date_backup b WHERE b.tbl = 'tb_course_sessions' AND b.id = x.id;
DROP TABLE demo_course_date_backup;

DO $$
DECLARE
    t TEXT;
BEGIN
    INSERT INTO tb_users SELECT (jsonb_populate_record(NULL::tb_users, b.row)).*
      FROM demo_downsize_backup b WHERE b.tbl = 'tb_users' ORDER BY b.seq ON CONFLICT (id) DO NOTHING;

    FOREACH t IN ARRAY ARRAY['tb_user_roles', 'tb_user_identities', 'tb_user_agreements', 'tb_user_interests',
                             'tb_password_reset_tokens',
                             'tb_portfolio_shares', 'tb_notifications', 'tb_course_reviews', 'tb_enrollments',
                             'tb_attendance_records', 'tb_completions', 'tb_course_interests',
                             'tb_recommendation_impressions', 'tb_course_recommendation_scores'] LOOP
        EXECUTE format('INSERT INTO %I SELECT (jsonb_populate_record(NULL::%I, b.row)).* FROM demo_downsize_backup b '
                       'WHERE b.tbl = %L ORDER BY b.seq ON CONFLICT DO NOTHING', t, t, t);
    END LOOP;

    UPDATE tb_courses c
       SET institution_id = (b.row ->> 'institution_id')::uuid,
           capacity = (b.row ->> 'capacity')::int,
           deleted_at = (b.row ->> 'deleted_at')::timestamptz
      FROM demo_downsize_backup b WHERE b.tbl = 'U:tb_courses' AND c.id = (b.row ->> 'id')::uuid;
    UPDATE tb_institutions i SET deleted_at = (b.row ->> 'deleted_at')::timestamptz
      FROM demo_downsize_backup b WHERE b.tbl = 'U:tb_institutions' AND i.id = (b.row ->> 'id')::uuid;
    UPDATE tb_enrollments e
       SET status = b.row ->> 'status', approved_at = (b.row ->> 'approved_at')::timestamptz
      FROM demo_downsize_backup b WHERE b.tbl = 'U:tb_enrollments' AND e.id = (b.row ->> 'id')::uuid;
END $$;

DROP TABLE demo_downsize_backup;
DROP TABLE demo_downsize_state;
