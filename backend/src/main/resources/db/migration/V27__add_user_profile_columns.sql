-- 관리자 목록·상세와 마이페이지가 요구하는 사용자 필드.
ALTER TABLE tb_users
    ADD COLUMN gender VARCHAR(10),
    ADD COLUMN region VARCHAR(50),
    ADD COLUMN last_login_at TIMESTAMPTZ;

ALTER TABLE tb_users ADD CONSTRAINT ck_tb_users_gender
    CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'UNDISCLOSED'));
