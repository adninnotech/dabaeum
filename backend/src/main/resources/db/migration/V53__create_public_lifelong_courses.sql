-- 공공데이터 평생학습강좌 연동 (설계서 docs/specs/public-lifelong-courses.md §4)
-- 외부 강좌는 조회 전용 카탈로그다. 행은 지우지 않고 수집에서 빠지면 비활성으로 표시한다.

CREATE TABLE tb_public_courses (
    id                    UUID         NOT NULL,
    source_key            VARCHAR(64)  NOT NULL,
    raw_hash              VARCHAR(64)  NOT NULL,
    instt_code            VARCHAR(20)  NOT NULL,
    instt_name            VARCHAR(500),
    lecture_name          VARCHAR(500) NOT NULL,
    instructor_name       VARCHAR(500),
    lecture_content       TEXT,
    place                 VARCHAR(500),
    road_address          VARCHAR(500),
    oper_institution_name VARCHAR(500),
    oper_phone            VARCHAR(500),
    homepage_url          VARCHAR(500),
    edc_start_date        DATE,
    edc_end_date          DATE,
    start_time            TIME,
    end_time              TIME,
    oper_days             TEXT[]       NOT NULL DEFAULT '{}',
    target_types          TEXT[]       NOT NULL DEFAULT '{}',
    method                VARCHAR(10),
    region_code           VARCHAR(2),
    capacity              INTEGER,
    cost_amount           INTEGER,
    free                  BOOLEAN,
    receipt_start_date    DATE,
    receipt_end_date      DATE,
    receipt_method        VARCHAR(500),
    selection_method      VARCHAR(500),
    training_subsidy      BOOLEAN,
    credit_bank           BOOLEAN,
    learning_account      BOOLEAN,
    reference_date        DATE,
    raw                   JSONB        NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    first_seen_at         TIMESTAMPTZ  NOT NULL,
    last_seen_at          TIMESTAMPTZ  NOT NULL,
    deactivated_at        TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_public_courses PRIMARY KEY (id),
    CONSTRAINT uq_tb_public_courses_source_key UNIQUE (source_key),
    CONSTRAINT ck_tb_public_courses_source_key CHECK (source_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tb_public_courses_edc_date CHECK (edc_start_date IS NOT NULL OR edc_end_date IS NOT NULL),
    CONSTRAINT ck_tb_public_courses_method CHECK (method IS NULL OR method IN ('OFFLINE', 'ONLINE', 'HYBRID', 'VISIT', 'OTHER')),
    CONSTRAINT ck_tb_public_courses_region CHECK (region_code IS NULL OR region_code ~ '^[0-9]{2}$'),
    CONSTRAINT ck_tb_public_courses_homepage CHECK (homepage_url IS NULL OR homepage_url ~* '^https?://'),
    CONSTRAINT ck_tb_public_courses_amounts CHECK ((capacity IS NULL OR capacity >= 0) AND (cost_amount IS NULL OR cost_amount >= 0)),
    CONSTRAINT ck_tb_public_courses_active CHECK (active = (deactivated_at IS NULL))
);

CREATE INDEX ix_tb_public_courses_active_end ON tb_public_courses (active, edc_end_date);
CREATE INDEX ix_tb_public_courses_instt ON tb_public_courses (instt_code, active);
CREATE INDEX ix_tb_public_courses_region ON tb_public_courses (region_code);
CREATE INDEX ix_tb_public_courses_target_types ON tb_public_courses USING gin (target_types);
CREATE INDEX ix_tb_public_courses_name_trgm ON tb_public_courses USING gin (LOWER(lecture_name) gin_trgm_ops);

CREATE TABLE tb_public_course_sync_runs (
    id                  UUID        NOT NULL,
    trigger_type        VARCHAR(10) NOT NULL,
    requested_by        UUID,
    status              VARCHAR(10) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    fetched             INTEGER     NOT NULL DEFAULT 0,
    inserted            INTEGER     NOT NULL DEFAULT 0,
    updated             INTEGER     NOT NULL DEFAULT 0,
    unchanged           INTEGER     NOT NULL DEFAULT 0,
    deactivated         INTEGER     NOT NULL DEFAULT 0,
    skipped             INTEGER     NOT NULL DEFAULT 0,
    parse_failures      INTEGER     NOT NULL DEFAULT 0,
    key_collisions      INTEGER     NOT NULL DEFAULT 0,
    institution_results JSONB       NOT NULL DEFAULT '[]',
    error_code          VARCHAR(40),
    CONSTRAINT pk_tb_public_course_sync_runs PRIMARY KEY (id),
    CONSTRAINT fk_tb_public_course_sync_runs_requested_by FOREIGN KEY (requested_by) REFERENCES tb_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_tb_public_course_sync_runs_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_tb_public_course_sync_runs_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_tb_public_course_sync_runs_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL))
);

CREATE INDEX ix_tb_public_course_sync_runs_started ON tb_public_course_sync_runs (started_at DESC);
-- 동시에 하나만 돈다. 수집은 외부 호출이 길어 advisory lock(트랜잭션 범위) 대신 이 유일성으로 막는다.
CREATE UNIQUE INDEX uq_tb_public_course_sync_runs_running ON tb_public_course_sync_runs (status) WHERE status = 'RUNNING';
