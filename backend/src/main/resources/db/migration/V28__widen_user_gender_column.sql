-- V27 은 이미 dev DB 에 적용돼 체크섬이 고정됐으므로 제자리 수정 대신 새 마이그레이션으로 넓힌다.
-- UNDISCLOSED 가 11자라 V27 의 VARCHAR(10) 에 들어가지 않는다.
-- 앞으로 값이 길어질 여지를 두고 20 으로 넓힌다. CHECK 제약은 그대로 유효하다.
ALTER TABLE tb_users
    ALTER COLUMN gender TYPE VARCHAR(20);
