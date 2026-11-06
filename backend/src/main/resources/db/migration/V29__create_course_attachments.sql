-- 강좌 첨부파일. 파일 자체는 tb_files 에 있고 여기는 연결만 한다.
CREATE TABLE tb_course_attachments (
    id UUID NOT NULL,
    course_id UUID NOT NULL,
    file_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_tb_course_attachments PRIMARY KEY (id),
    CONSTRAINT fk_tb_course_attachments_course
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_course_attachments_file
        FOREIGN KEY (file_id) REFERENCES tb_files (id) ON DELETE RESTRICT
);

-- 같은 파일을 한 강좌에 두 번 붙이지 않는다.
CREATE UNIQUE INDEX uq_tb_course_attachments_active
    ON tb_course_attachments (course_id, file_id) WHERE deleted_at IS NULL;

CREATE INDEX ix_tb_course_attachments_course
    ON tb_course_attachments (course_id, sort_order) WHERE deleted_at IS NULL;

-- 강좌 첨부파일 목적을 tb_files 의 purpose CHECK 에 추가한다. 기존 행은 세 값 중 하나이므로
-- 새 제약을 그대로 통과한다.
ALTER TABLE tb_files DROP CONSTRAINT ck_tb_files_purpose;
ALTER TABLE tb_files ADD CONSTRAINT ck_tb_files_purpose
    CHECK (purpose IN ('PROFILE', 'COURSE_THUMBNAIL', 'INQUIRY', 'COURSE_ATTACHMENT'));
