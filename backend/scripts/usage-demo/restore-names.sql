-- 시연용으로 바꾼 가상 강좌·기관 이름과 가상 회원 이름·가입일·나이·성별을 원래대로 돌린다 (docs/specs/usage-statistics.md §12). dabaeum_dev 전용.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'demo names are for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS demo_name_backup (kind VARCHAR(20) NOT NULL, id UUID NOT NULL, original VARCHAR(500) NOT NULL,
                                             PRIMARY KEY (kind, id));
UPDATE tb_courses c SET title = b.original FROM demo_name_backup b WHERE b.kind = 'COURSE' AND b.id = c.id;
UPDATE tb_institutions i SET name = b.original FROM demo_name_backup b WHERE b.kind = 'INSTITUTION' AND b.id = i.id;
UPDATE tb_users u SET created_at = b.original::timestamptz FROM demo_name_backup b
 WHERE b.kind = 'USER_CREATED_AT' AND b.id = u.id;
UPDATE tb_users u SET name = b.original FROM demo_name_backup b WHERE b.kind = 'USER_NAME' AND b.id = u.id;
UPDATE tb_users u SET birth_date = NULLIF(b.original, '')::date FROM demo_name_backup b
 WHERE b.kind = 'USER_BIRTH_DATE' AND b.id = u.id;
UPDATE tb_users u SET gender = NULLIF(b.original, '') FROM demo_name_backup b WHERE b.kind = 'USER_GENDER' AND b.id = u.id;
DROP TABLE demo_name_backup;
