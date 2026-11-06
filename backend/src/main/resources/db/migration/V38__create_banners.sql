-- B-10 배너 관리 / E-1 홈 최상단 캐러셀.
CREATE TABLE tb_banners (
    id UUID NOT NULL,
    badge_text VARCHAR(60),
    title VARCHAR(60) NOT NULL,
    subtitle VARCHAR(60),
    button_text VARCHAR(30),
    button_link VARCHAR(1000),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    sort_order INTEGER NOT NULL DEFAULT 0,
    pc_image_file_id UUID,
    mobile_image_file_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_tb_banners PRIMARY KEY (id),
    CONSTRAINT fk_tb_banners_pc_image
        FOREIGN KEY (pc_image_file_id) REFERENCES tb_files (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_banners_mobile_image
        FOREIGN KEY (mobile_image_file_id) REFERENCES tb_files (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_banners_visibility
        CHECK (visibility IN ('PUBLIC', 'SCHEDULED', 'PRIVATE')),
    CONSTRAINT ck_tb_banners_period
        CHECK (start_at IS NULL OR end_at IS NULL OR start_at <= end_at),
    -- 기간 공개면 시작·종료가 둘 다 있어야 한다.
    CONSTRAINT ck_tb_banners_scheduled_period
        CHECK (visibility <> 'SCHEDULED'
               OR (start_at IS NOT NULL AND end_at IS NOT NULL))
);

CREATE INDEX ix_tb_banners_public
    ON tb_banners (sort_order, created_at DESC) WHERE deleted_at IS NULL;

-- 배너 이미지 목적을 tb_files 의 purpose CHECK 에 추가한다. 기존 행은 그대로 통과한다.
ALTER TABLE tb_files DROP CONSTRAINT ck_tb_files_purpose;
ALTER TABLE tb_files ADD CONSTRAINT ck_tb_files_purpose
    CHECK (purpose IN
        ('PROFILE', 'COURSE_THUMBNAIL', 'INQUIRY', 'COURSE_ATTACHMENT', 'INSTITUTION_LOGO',
         'NOTICE_ATTACHMENT', 'BANNER'));
