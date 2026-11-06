-- 평생학습플랫폼 고도화 계획 2 — 로그인 보호, 관리자 비밀번호 변경 시각, 관리자 접속기록 (설계서 §10)

-- §10.3 로그인 보호. 서버가 여러 대여도 같은 값을 보도록 DB 에 둔다.
ALTER TABLE tb_users
    ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_tb_users_failed_login_count CHECK (failed_login_count >= 0);

-- §10.3 관리자 비밀번호 90일. NULL 은 "임시 비밀번호 — 본인이 바꾼 적 없음".
ALTER TABLE tb_user_identities
    ADD COLUMN password_changed_at TIMESTAMPTZ;

UPDATE tb_user_identities
   SET password_changed_at = updated_at
 WHERE provider = 'LOCAL'
   AND password_hash IS NOT NULL;

ALTER TABLE tb_user_identities
    ADD CONSTRAINT ck_tb_user_identities_password_changed_at
    CHECK (password_hash IS NOT NULL OR password_changed_at IS NULL);

-- §10.2 관리자 접속기록 (개인정보의 안전성 확보조치 기준 제8조).
-- 사용자 FK 를 두지 않는다 — 기록은 계정보다 오래 남아야 한다.
CREATE TABLE tb_admin_access_logs (
    id              UUID         NOT NULL,
    actor_id        UUID         NOT NULL,
    actor_role      VARCHAR(30)  NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    endpoint        VARCHAR(200) NOT NULL,
    target_user_ids UUID[],
    result_count    INTEGER      NOT NULL,
    status_code     INTEGER      NOT NULL,
    ip_address      INET,
    user_agent      VARCHAR(500),
    request_id      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_admin_access_logs PRIMARY KEY (id),
    CONSTRAINT ck_tb_admin_access_logs_actor_role
        CHECK (actor_role IN ('PLATFORM_ADMIN', 'INSTITUTION_ADMIN')),
    CONSTRAINT ck_tb_admin_access_logs_action
        CHECK (action IN ('READ', 'UPDATE', 'DELETE', 'DOWNLOAD')),
    CONSTRAINT ck_tb_admin_access_logs_result_count CHECK (result_count >= 0),
    CONSTRAINT ck_tb_admin_access_logs_status_code CHECK (status_code BETWEEN 100 AND 599),
    CONSTRAINT ck_tb_admin_access_logs_targets
        CHECK (target_user_ids IS NULL OR cardinality(target_user_ids) <= 1000)
);

CREATE INDEX ix_tb_admin_access_logs_actor ON tb_admin_access_logs (actor_id, created_at DESC);
CREATE INDEX ix_tb_admin_access_logs_date ON tb_admin_access_logs (created_at);

-- 접속기록은 고치지 않는다. 보존 기간이 지난 행의 삭제만 허용한다.
CREATE FUNCTION fn_tb_admin_access_logs_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'tb_admin_access_logs is append-only'
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tb_admin_access_logs_append_only
    BEFORE UPDATE ON tb_admin_access_logs
    FOR EACH ROW EXECUTE FUNCTION fn_tb_admin_access_logs_append_only();
