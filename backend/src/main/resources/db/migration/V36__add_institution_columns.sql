-- B-5 강사 그룹 관리·상세가 요구하는 기관 필드.
ALTER TABLE tb_institutions
    ADD COLUMN homepage_url VARCHAR(500),
    ADD COLUMN region VARCHAR(50),
    ADD COLUMN logo_file_id UUID;

ALTER TABLE tb_institutions
    ADD CONSTRAINT fk_tb_institutions_logo_file
        FOREIGN KEY (logo_file_id) REFERENCES tb_files (id) ON DELETE RESTRICT;

-- 기관 로고 목적을 tb_files 의 purpose CHECK 에 추가한다. 기존 행은 그대로 통과한다.
ALTER TABLE tb_files DROP CONSTRAINT ck_tb_files_purpose;
ALTER TABLE tb_files ADD CONSTRAINT ck_tb_files_purpose
    CHECK (purpose IN
        ('PROFILE', 'COURSE_THUMBNAIL', 'INQUIRY', 'COURSE_ATTACHMENT', 'INSTITUTION_LOGO'));
