-- 현장검사용: 가상 데이터를 "2026-08-17 오픈 뒤 두 달 반 운영한 포털" 규모로 줄인다 (docs/specs/usage-statistics.md §12 결정 15).
-- dabaeum_dev 전용. 목표: 가상 기관 6곳(SYN-INST-01~06), 가상 강좌 60개(모집 18·진행 17·종료 25), 가상 회원 650명,
-- 정원 15명(가끔 20명), 모집 중 강좌는 정원의 40~80% 승인·대기 0~3명, 지난 강좌는 정원의 60~100%.
-- 데모 계정 3개(syn-00617·01091·01775)와 그 신청·강좌는 반드시 남긴다. QA·실제 계정과 그 데이터는 건드리지 않는다.
-- 지우거나 바꾸는 행은 모두 demo_downsize_backup 에 원래 모습 그대로 남기고 restore-downsize.sql 이 되돌린다.
-- 고정 시드라 같은 상태에서 다시 돌리면 같은 결과다. 이미 줄였으면(완료 표시가 있으면) 아무것도 하지 않는다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'demo downsize is for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS demo_downsize_backup (
    seq BIGSERIAL PRIMARY KEY,
    tbl TEXT      NOT NULL,
    row JSONB     NOT NULL
);
CREATE TABLE IF NOT EXISTS demo_downsize_state (
    k  TEXT        PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
DECLARE
    demo_emails CONSTANT TEXT[] := ARRAY['syn-00617@synthetic.invalid', 'syn-01091@synthetic.invalid',
                                         'syn-01775@synthetic.invalid'];
    kept_institutions CONSTANT TEXT[] := ARRAY['SYN-INST-01', 'SYN-INST-02', 'SYN-INST-03', 'SYN-INST-04',
                                               'SYN-INST-05', 'SYN-INST-06'];
    target RECORD;
    dep RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM demo_downsize_state WHERE k = 'done') THEN
        RAISE NOTICE 'demo downsize already applied; nothing to do';
        RETURN;
    END IF;
    PERFORM setseed(0.2026);

    -- 1. 가상 강좌와 데모 계정이 신청한 강좌
    CREATE TEMP TABLE ds_courses ON COMMIT DROP AS
    SELECT c.id, c.status, i.institution_code AS inst, left(c.classification_code, 9) AS cls,
           EXISTS (SELECT 1 FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id
                    WHERE e.course_id = c.id AND u.email = ANY (demo_emails)) AS required,
           random() AS r
      FROM tb_courses c JOIN tb_institutions i ON i.id = c.institution_id
     WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL;

    -- 2. 남길 강좌: 데모 강좌 + 상태별 목표까지 기관 01~06 에서 분류를 돌아가며 채운다
    CREATE TEMP TABLE ds_keep_courses (id UUID PRIMARY KEY, status TEXT, capacity INT, fill NUMERIC) ON COMMIT DROP;
    INSERT INTO ds_keep_courses (id, status) SELECT id, status FROM ds_courses WHERE required;
    FOR target IN SELECT * FROM (VALUES ('RECRUITING', 18), ('IN_PROGRESS', 17), ('COMPLETED', 25)) AS t(status, n) LOOP
        INSERT INTO ds_keep_courses (id, status)
        SELECT id, status FROM (
            SELECT c.id, c.status,
                   row_number() OVER (PARTITION BY c.cls ORDER BY c.r) AS round_robin, c.r
              FROM ds_courses c
             WHERE c.status = target.status AND NOT c.required AND c.inst = ANY (kept_institutions)) x
         ORDER BY round_robin, r
         LIMIT GREATEST(target.n - (SELECT COUNT(*) FROM ds_keep_courses k WHERE k.status = target.status), 0);
    END LOOP;
    -- 정원: 85% 15명, 15% 20명. 채움: 모집 중 40~80%, 지난 강좌 60~100%
    UPDATE ds_keep_courses SET capacity = CASE WHEN random() < 0.15 THEN 20 ELSE 15 END;
    UPDATE ds_keep_courses SET fill = CASE WHEN status = 'RECRUITING' THEN 0.4 + random() * 0.4 ELSE 0.6 + random() * 0.4 END;

    -- 3. 강좌·기관 원래 모습 백업(바꾸거나 숨기는 것 전부)
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'U:tb_courses', to_jsonb(c) FROM tb_courses c JOIN ds_courses d ON d.id = c.id;
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'U:tb_institutions', to_jsonb(i) FROM tb_institutions i
     WHERE i.institution_code LIKE 'SYN-%' AND NOT (i.institution_code = ANY (kept_institutions));

    -- 4. 기관 07~12 의 데모 강좌는 남기는 기관(07→01 … 12→06)으로 옮기고, 남지 않는 강좌·기관은 삭제 처리
    UPDATE tb_courses c
       SET institution_id = (SELECT id FROM tb_institutions
                              WHERE institution_code = 'SYN-INST-0' || (substring(d.inst FROM 10)::int - 6))
      FROM ds_courses d JOIN ds_keep_courses k ON k.id = d.id
     WHERE c.id = d.id AND NOT (d.inst = ANY (kept_institutions));
    UPDATE tb_courses c SET capacity = k.capacity FROM ds_keep_courses k WHERE c.id = k.id;
    UPDATE tb_courses c SET deleted_at = now()
      FROM ds_courses d WHERE c.id = d.id AND NOT EXISTS (SELECT 1 FROM ds_keep_courses k WHERE k.id = d.id);
    UPDATE tb_institutions SET deleted_at = now()
     WHERE institution_code LIKE 'SYN-%' AND NOT (institution_code = ANY (kept_institutions)) AND deleted_at IS NULL;

    -- 5. 남길 회원 650명: 데모 3 + 남는 강좌에 신청이 있는 회원 470 + 신청 없는 회원 177(가입만 한 회원, 약 28%)
    CREATE TEMP TABLE ds_keep_users (id UUID PRIMARY KEY) ON COMMIT DROP;
    INSERT INTO ds_keep_users SELECT id FROM tb_users WHERE email = ANY (demo_emails);
    INSERT INTO ds_keep_users
    SELECT u.id FROM tb_users u
     WHERE u.email LIKE '%@synthetic.invalid' AND NOT (u.email = ANY (demo_emails))
       AND EXISTS (SELECT 1 FROM tb_enrollments e JOIN ds_keep_courses k ON k.id = e.course_id WHERE e.user_id = u.id)
     ORDER BY random() LIMIT 470;
    INSERT INTO ds_keep_users
    SELECT u.id FROM tb_users u
     WHERE u.email LIKE '%@synthetic.invalid' AND NOT EXISTS (SELECT 1 FROM ds_keep_users k WHERE k.id = u.id)
     ORDER BY random() LIMIT 177;

    -- 6. 남길 신청: 남는 강좌 × 남는 회원 중에서 강좌마다 정원 × 채움 만큼(데모 신청 먼저, 수료 있는 것 먼저).
    --    모집 중 강좌는 남긴 신청을 승인으로 바꾸고, 대기(APPLIED) 0~3건을 더 남긴다. 데모 계정 신청은 모두 남긴다.
    CREATE TEMP TABLE ds_keep_enrollments (id UUID PRIMARY KEY, approve BOOLEAN) ON COMMIT DROP;
    INSERT INTO ds_keep_enrollments (id, approve)
    SELECT e.id, FALSE FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id WHERE u.email = ANY (demo_emails);
    INSERT INTO ds_keep_enrollments (id, approve)
    SELECT id, approve FROM (
        SELECT e.id, k.status = 'RECRUITING' AS approve,
               row_number() OVER (PARTITION BY e.course_id
                                  ORDER BY EXISTS (SELECT 1 FROM tb_completions cp WHERE cp.enrollment_id = e.id) DESC,
                                           random()) AS rn,
               round(k.capacity * k.fill)::int
                   - (SELECT COUNT(*) FROM tb_enrollments d JOIN tb_users du ON du.id = d.user_id
                       WHERE d.course_id = e.course_id AND du.email = ANY (demo_emails)) AS quota
          FROM tb_enrollments e
          JOIN ds_keep_courses k ON k.id = e.course_id
          JOIN ds_keep_users ku ON ku.id = e.user_id
          JOIN tb_users u ON u.id = e.user_id
         WHERE NOT (u.email = ANY (demo_emails)) AND e.status IN ('APPROVED', 'APPLIED')) x
     WHERE rn <= quota
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO ds_keep_enrollments (id, approve)
    SELECT id, FALSE FROM (
        SELECT e.id, row_number() OVER (PARTITION BY e.course_id ORDER BY random()) AS rn, floor(random() * 4)::int AS pending
          FROM tb_enrollments e
          JOIN ds_keep_courses k ON k.id = e.course_id AND k.status = 'RECRUITING'
          JOIN ds_keep_users ku ON ku.id = e.user_id
         WHERE e.status = 'APPLIED' AND NOT EXISTS (SELECT 1 FROM ds_keep_enrollments x WHERE x.id = e.id)) y
     WHERE rn <= pending
    ON CONFLICT (id) DO NOTHING;

    -- 7. 지울 신청: 가상 회원의 신청 또는 가상 강좌의 신청 중 남기지 않는 것(QA·실제 계정 신청은 제외)
    CREATE TEMP TABLE ds_drop_enrollments ON COMMIT DROP AS
    SELECT e.id FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id
     WHERE u.email LIKE '%@synthetic.invalid' AND NOT EXISTS (SELECT 1 FROM ds_keep_enrollments k WHERE k.id = e.id);

    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'tb_completions', to_jsonb(cp) FROM tb_completions cp JOIN ds_drop_enrollments d ON d.id = cp.enrollment_id;
    DELETE FROM tb_completions cp USING ds_drop_enrollments d WHERE cp.enrollment_id = d.id;
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'tb_attendance_records', to_jsonb(a) FROM tb_attendance_records a JOIN ds_drop_enrollments d ON d.id = a.enrollment_id;
    DELETE FROM tb_attendance_records a USING ds_drop_enrollments d WHERE a.enrollment_id = d.id;
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'tb_enrollments', to_jsonb(e) FROM tb_enrollments e JOIN ds_drop_enrollments d ON d.id = e.id;
    DELETE FROM tb_enrollments e USING ds_drop_enrollments d WHERE e.id = d.id;

    -- 8. 모집 중 강좌의 남긴 신청은 승인으로(신청 1~3일 뒤 승인)
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'U:tb_enrollments', to_jsonb(e) FROM tb_enrollments e JOIN ds_keep_enrollments k ON k.id = e.id
     WHERE k.approve AND e.status <> 'APPROVED';
    UPDATE tb_enrollments e
       SET status = 'APPROVED',
           approved_at = LEAST(e.applied_at + (1 + random() * 2) * INTERVAL '1 day', now())
      FROM ds_keep_enrollments k WHERE k.id = e.id AND k.approve AND e.status <> 'APPROVED';

    -- 9. 남는 회원의 찜 중 숨긴 강좌 것은 지운다
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'tb_course_interests', to_jsonb(ci) FROM tb_course_interests ci
      JOIN ds_keep_users ku ON ku.id = ci.user_id
      JOIN ds_courses d ON d.id = ci.course_id
     WHERE NOT EXISTS (SELECT 1 FROM ds_keep_courses k WHERE k.id = ci.course_id);
    DELETE FROM tb_course_interests ci USING ds_keep_users ku, ds_courses d
     WHERE ku.id = ci.user_id AND d.id = ci.course_id AND NOT EXISTS (SELECT 1 FROM ds_keep_courses k WHERE k.id = ci.course_id);

    -- 10. 남지 않는 회원: 딸린 행을 백업하고 지운 뒤 회원을 지운다(관심 분야는 회원 삭제로 함께 지워지므로 먼저 백업한다)
    CREATE TEMP TABLE ds_drop_users ON COMMIT DROP AS
    SELECT u.id FROM tb_users u
     WHERE u.email LIKE '%@synthetic.invalid' AND NOT EXISTS (SELECT 1 FROM ds_keep_users k WHERE k.id = u.id);
    FOR dep IN SELECT * FROM (VALUES
            ('tb_course_recommendation_scores', 'user_id'), ('tb_recommendation_impressions', 'user_id'),
            ('tb_course_interests', 'user_id'), ('tb_course_reviews', 'user_id'), ('tb_notifications', 'user_id'),
            ('tb_portfolio_shares', 'user_id'), ('tb_password_reset_tokens', 'user_id'),
            ('tb_user_agreements', 'user_id'), ('tb_user_interests', 'user_id'), ('tb_user_identities', 'user_id'), ('tb_user_roles', 'user_id')
        ) AS t(tbl, col) LOOP
        EXECUTE format('INSERT INTO demo_downsize_backup (tbl, row) SELECT %L, to_jsonb(x) FROM %I x '
                       'JOIN ds_drop_users d ON d.id = x.%I', dep.tbl, dep.tbl, dep.col);
        EXECUTE format('DELETE FROM %I x USING ds_drop_users d WHERE x.%I = d.id', dep.tbl, dep.col);
    END LOOP;
    INSERT INTO demo_downsize_backup (tbl, row)
    SELECT 'tb_users', to_jsonb(u) FROM tb_users u JOIN ds_drop_users d ON d.id = u.id;
    DELETE FROM tb_users u USING ds_drop_users d WHERE u.id = d.id;

    INSERT INTO demo_downsize_state (k) VALUES ('done');
END $$;
