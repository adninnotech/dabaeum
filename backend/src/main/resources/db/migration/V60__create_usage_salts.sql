-- 이용 통계 전용 일별 salt (docs/specs/usage-statistics.md §3.1).
-- 회원 키는 HMAC(salt, 회원 id) 라서 salt 가 남아 있으면 회원 목록으로 키를 다시 계산해 기록을 회원에 이을 수 있다.
-- 그래서 강좌 조회 salt(tb_view_salts, 1년)와 나누고, 그날이 지나면 지운다(매일 04:30 보존 작업).
CREATE TABLE tb_usage_salts (
    salt_date DATE  NOT NULL,
    salt      BYTEA NOT NULL,
    CONSTRAINT pk_tb_usage_salts PRIMARY KEY (salt_date)
);
