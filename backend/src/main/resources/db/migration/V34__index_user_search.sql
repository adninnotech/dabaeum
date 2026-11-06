-- pg_trgm 은 PostgreSQL 13+ 에서 신뢰 확장(trusted extension)이다.
-- 슈퍼유저가 아니어도 데이터베이스에 CREATE 권한만 있으면 설치할 수 있다.
-- LIKE '%...%' 는 인덱스를 못 타므로, GIN 트라이그램 인덱스로 태울 수 있게 한다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS ix_tb_users_name_trgm
    ON tb_users USING gin (LOWER(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_tb_users_email_trgm
    ON tb_users USING gin (LOWER(email) gin_trgm_ops);
