-- 관리자 정정 이력.
-- 과정·회차·이수의 상태 전이는 단방향이라 운영 중 실수를 API 로 바로잡을 길이 없었고,
-- 그래서 DB 를 직접 고치는 것이 사실상 표준 절차가 되어 감사 흔적이 남지 않았다.
-- 정정 API 를 열되 누가 무엇을 왜 되돌렸는지 여기에 남긴다.

CREATE TABLE tb_admin_corrections (
    id UUID NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id UUID NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    corrected_by UUID NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_admin_corrections PRIMARY KEY (id),
    CONSTRAINT fk_tb_admin_corrections_corrected_by
        FOREIGN KEY (corrected_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_admin_corrections_target_type
        CHECK (target_type IN ('COURSE', 'COURSE_SESSION', 'COMPLETION')),
    CONSTRAINT ck_tb_admin_corrections_reason
        CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_tb_admin_corrections_target
    ON tb_admin_corrections (target_type, target_id, corrected_at DESC);
