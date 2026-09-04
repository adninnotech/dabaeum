-- 프론트 미구현 API 지원 테이블 일괄 생성.
-- 공지, FAQ, 문의, 수강평, 관심 강좌, 알림, 약관, 공통코드, 파일,
-- 기관 가입 신청, 비밀번호 재설정 토큰과 프로필·썸네일·메모 컬럼 확장을 포함한다.

CREATE TABLE tb_notices (
    id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    audience VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_notices PRIMARY KEY (id),
    CONSTRAINT fk_tb_notices_created_by
        FOREIGN KEY (created_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_notices_audience
        CHECK (audience IN ('PUBLIC', 'INSTRUCTOR', 'INSTITUTION')),
    CONSTRAINT ck_tb_notices_status
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_tb_notices_published_state CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
    )
);

CREATE INDEX ix_tb_notices_status_published_at
    ON tb_notices (status, published_at DESC);

CREATE TABLE tb_faqs (
    id UUID NOT NULL,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_faqs PRIMARY KEY (id)
);

CREATE TABLE tb_inquiries (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    course_id UUID,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    answer TEXT,
    answered_by UUID,
    answered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_inquiries PRIMARY KEY (id),
    CONSTRAINT fk_tb_inquiries_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_inquiries_course
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_inquiries_answered_by
        FOREIGN KEY (answered_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_inquiries_status
        CHECK (status IN ('PENDING', 'ANSWERED')),
    CONSTRAINT ck_tb_inquiries_answer_state CHECK (
        (status = 'PENDING' AND answer IS NULL
            AND answered_by IS NULL AND answered_at IS NULL)
        OR (status = 'ANSWERED' AND answer IS NOT NULL
            AND answered_by IS NOT NULL AND answered_at IS NOT NULL)
    )
);

CREATE INDEX ix_tb_inquiries_user_created_at
    ON tb_inquiries (user_id, created_at DESC);

CREATE INDEX ix_tb_inquiries_course_created_at
    ON tb_inquiries (course_id, created_at DESC)
 WHERE course_id IS NOT NULL;

CREATE TABLE tb_course_reviews (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    course_id UUID NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_course_reviews PRIMARY KEY (id),
    CONSTRAINT fk_tb_course_reviews_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_course_reviews_course
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_course_reviews_rating
        CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_tb_course_reviews_user_course
        UNIQUE (user_id, course_id)
);

CREATE INDEX ix_tb_course_reviews_course_created_at
    ON tb_course_reviews (course_id, created_at DESC);

CREATE TABLE tb_course_interests (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    course_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_course_interests PRIMARY KEY (id),
    CONSTRAINT fk_tb_course_interests_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_course_interests_course
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_course_interests_user_course
        UNIQUE (user_id, course_id)
);

CREATE INDEX ix_tb_course_interests_user_created_at
    ON tb_course_interests (user_id, created_at DESC);

CREATE TABLE tb_notifications (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(2000),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_notifications PRIMARY KEY (id),
    CONSTRAINT fk_tb_notifications_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_tb_notifications_user_created_at
    ON tb_notifications (user_id, created_at DESC);

CREATE TABLE tb_terms_contents (
    id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    version VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_terms_contents PRIMARY KEY (id),
    CONSTRAINT ck_tb_terms_contents_type
        CHECK (type IN ('USAGE', 'PRIVACY')),
    CONSTRAINT uq_tb_terms_contents_type_version
        UNIQUE (type, version)
);

CREATE TABLE tb_common_codes (
    id UUID NOT NULL,
    code_group VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_common_codes PRIMARY KEY (id),
    CONSTRAINT uq_tb_common_codes_group_code
        UNIQUE (code_group, code)
);

CREATE TABLE tb_files (
    id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_files PRIMARY KEY (id),
    CONSTRAINT fk_tb_files_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_files_purpose
        CHECK (purpose IN ('PROFILE', 'COURSE_THUMBNAIL', 'INQUIRY')),
    CONSTRAINT ck_tb_files_size CHECK (size > 0)
);

CREATE TABLE tb_institution_applications (
    id UUID NOT NULL,
    institution_name VARCHAR(200) NOT NULL,
    institution_code VARCHAR(50),
    representative_name VARCHAR(100) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50) NOT NULL,
    address VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(1000),
    applicant_user_id UUID,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    created_institution_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_institution_applications PRIMARY KEY (id),
    CONSTRAINT fk_tb_institution_applications_applicant
        FOREIGN KEY (applicant_user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_institution_applications_decided_by
        FOREIGN KEY (decided_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_institution_applications_institution
        FOREIGN KEY (created_institution_id)
        REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_institution_applications_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_tb_institution_applications_state CHECK (
        (status = 'PENDING' AND decided_by IS NULL
            AND decided_at IS NULL AND rejection_reason IS NULL
            AND created_institution_id IS NULL)
        OR (status = 'APPROVED' AND decided_by IS NOT NULL
            AND decided_at IS NOT NULL AND rejection_reason IS NULL
            AND created_institution_id IS NOT NULL)
        OR (status = 'REJECTED' AND decided_by IS NOT NULL
            AND decided_at IS NOT NULL AND rejection_reason IS NOT NULL
            AND created_institution_id IS NULL)
    )
);

CREATE INDEX ix_tb_institution_applications_status_created_at
    ON tb_institution_applications (status, created_at DESC);

CREATE TABLE tb_password_reset_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_tb_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_tb_password_reset_tokens_user
    ON tb_password_reset_tokens (user_id, expires_at DESC);

-- 동일 Credential에 같은 종류·이름의 Badge 중복 발급을 DB 수준에서 차단한다.
CREATE UNIQUE INDEX uq_tb_learning_badges_credential_type_name
    ON tb_learning_badges (credential_id, badge_type, badge_name)
 WHERE credential_id IS NOT NULL;

-- 프로필·썸네일·기관 강사 메모 확장 컬럼.
ALTER TABLE tb_users
    ADD COLUMN career VARCHAR(2000),
    ADD COLUMN introduction VARCHAR(2000),
    ADD COLUMN profile_image_id UUID;

ALTER TABLE tb_users
    ADD CONSTRAINT fk_tb_users_profile_image
        FOREIGN KEY (profile_image_id) REFERENCES tb_files (id) ON DELETE RESTRICT;

ALTER TABLE tb_courses
    ADD COLUMN thumbnail_file_id UUID;

ALTER TABLE tb_courses
    ADD CONSTRAINT fk_tb_courses_thumbnail_file
        FOREIGN KEY (thumbnail_file_id) REFERENCES tb_files (id) ON DELETE RESTRICT;

ALTER TABLE tb_user_roles
    ADD COLUMN memo VARCHAR(1000);
