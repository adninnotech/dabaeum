-- C-6 · E-2 의 문의 유형 배지. 기존 행은 전부 강의 문의로 본다.
ALTER TABLE tb_inquiries
    ADD COLUMN inquiry_type VARCHAR(20) NOT NULL DEFAULT 'COURSE';

ALTER TABLE tb_inquiries ADD CONSTRAINT ck_tb_inquiries_type
    CHECK (inquiry_type IN ('COURSE', 'QNA'));

-- 강좌별 조회가 새 경로로 생기므로 인덱스를 확인한다.
CREATE INDEX IF NOT EXISTS ix_tb_inquiries_course_status
    ON tb_inquiries (course_id, status, created_at DESC);
