-- 피그마 D-1·D-3·E-2 화면이 요구하는 강좌 상세 필드.
-- 전부 nullable 또는 DEFAULT 를 가지므로 기존 행은 그대로 유효하다.
ALTER TABLE tb_courses
    ADD COLUMN tags VARCHAR(60),
    ADD COLUMN target_audiences TEXT,
    ADD COLUMN instructor_profile_visible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN tuition_type VARCHAR(10) NOT NULL DEFAULT 'FREE',
    ADD COLUMN tuition_amount NUMERIC(12, 0),
    ADD COLUMN material_fee_type VARCHAR(10) NOT NULL DEFAULT 'FREE',
    ADD COLUMN material_fee_amount NUMERIC(12, 0),
    ADD COLUMN completion_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN completion_criteria VARCHAR(20),
    ADD COLUMN required_progress_rate INTEGER,
    ADD COLUMN issue_certificate BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notes TEXT,
    ADD COLUMN materials VARCHAR(500),
    ADD COLUMN learning_objective TEXT,
    ADD COLUMN selection_method VARCHAR(20) NOT NULL DEFAULT 'FCFS',
    ADD COLUMN waitlist_capacity INTEGER;

ALTER TABLE tb_courses
    ADD CONSTRAINT ck_tb_courses_tuition_type
        CHECK (tuition_type IN ('FREE', 'PAID')),
    ADD CONSTRAINT ck_tb_courses_material_fee_type
        CHECK (material_fee_type IN ('FREE', 'PAID')),
    ADD CONSTRAINT ck_tb_courses_completion_criteria
        CHECK (completion_criteria IS NULL
               OR completion_criteria IN ('PROGRESS', 'ASSESSMENT')),
    ADD CONSTRAINT ck_tb_courses_selection_method
        CHECK (selection_method IN ('FCFS', 'LOTTERY')),
    ADD CONSTRAINT ck_tb_courses_required_progress_rate
        CHECK (required_progress_rate IS NULL
               OR required_progress_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_tb_courses_waitlist_capacity
        CHECK (waitlist_capacity IS NULL OR waitlist_capacity >= 0),
    -- 유료면 금액이 있어야 한다. 무료면 금액은 무시한다(NULL 강제는 하지 않는다).
    ADD CONSTRAINT ck_tb_courses_tuition_amount
        CHECK (tuition_type <> 'PAID' OR tuition_amount IS NOT NULL),
    ADD CONSTRAINT ck_tb_courses_material_fee_amount
        CHECK (material_fee_type <> 'PAID' OR material_fee_amount IS NOT NULL);
