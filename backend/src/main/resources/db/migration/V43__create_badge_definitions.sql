-- 평생학습플랫폼 고도화 계획 4 — 학습배지 정의와 자동 발급 (설계서 §11)
-- 규칙은 COURSE_COMPLETION({courseId} 또는 {classificationCode}) / CUMULATIVE({count, classificationCode?}) 두 가지뿐이다.

CREATE TABLE tb_badge_definitions (
    id            UUID         NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    image_file_id UUID,
    rule_type     VARCHAR(30)  NOT NULL,
    rule_params   JSONB        NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_badge_definitions PRIMARY KEY (id),
    CONSTRAINT uq_tb_badge_definitions_code UNIQUE (code),
    CONSTRAINT fk_tb_badge_definitions_image
        FOREIGN KEY (image_file_id) REFERENCES tb_files (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_badge_definitions_code CHECK (code ~ '^[A-Z0-9_]{2,50}$'),
    CONSTRAINT ck_tb_badge_definitions_rule CHECK (rule_type IN ('COURSE_COMPLETION', 'CUMULATIVE'))
);

-- 정의로 준 배지는 사람당 정의당 1회. 서버가 여러 대여도 이 인덱스가 중복을 막는다(설계서 §2.1).
ALTER TABLE tb_learning_badges
    ADD COLUMN definition_id UUID
        CONSTRAINT fk_tb_learning_badges_definition
        REFERENCES tb_badge_definitions (id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_tb_learning_badges_user_definition
    ON tb_learning_badges (user_id, definition_id)
 WHERE definition_id IS NOT NULL;
