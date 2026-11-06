-- 평생학번 (설계서 §3). 구·군 코드와 학번 컬럼, (YY, 구군)별 채번 카운터, 채번·불변 트리거.
-- 채번은 DB 가 한다 — 앱은 learner_no 를 비워 INSERT 한다(JPA IDENTITY 와 같은 방식).
-- 다대구 2단계 가입의 1회용 토큰 (§4.2) 도 여기서 만든다.

CREATE TABLE tb_learner_no_counters (
    yy CHAR(2) NOT NULL,
    region_code CHAR(2) NOT NULL,
    last_seq INTEGER NOT NULL,
    CONSTRAINT pk_tb_learner_no_counters PRIMARY KEY (yy, region_code),
    -- 99999 를 넘기면 이 CHECK 가 가입을 막는다. 앱은 이 이름으로 소진을 판정해 503 을 낸다.
    CONSTRAINT ck_tb_learner_no_counters_seq CHECK (last_seq BETWEEN 1 AND 99999)
);

-- region_code 기본값 99 는 직접 SQL 로 사용자를 넣는 테스트·스크립트를 위한 것이다.
-- 구·군 필수는 가입 API 가 검사한다.
ALTER TABLE tb_users
    ADD COLUMN region_code CHAR(2) NOT NULL DEFAULT '99',
    ADD COLUMN learner_no CHAR(9);

ALTER TABLE tb_users
    ADD CONSTRAINT ck_tb_users_region_code
        CHECK (region_code IN ('11', '14', '17', '20', '23', '26', '29', '71', '72', '99'));

-- 기존(dev) 행 채번: 가입 시각 순서. 운영 회원은 없다.
WITH numbered AS (
    SELECT id,
           to_char(created_at AT TIME ZONE 'Asia/Seoul', 'YY') AS yy,
           region_code,
           ROW_NUMBER() OVER (
               PARTITION BY to_char(created_at AT TIME ZONE 'Asia/Seoul', 'YY'), region_code
               ORDER BY created_at, id
           ) AS seq
      FROM tb_users
)
UPDATE tb_users u
   SET learner_no = n.yy || n.region_code || lpad(n.seq::text, 5, '0')
  FROM numbered n
 WHERE u.id = n.id;

INSERT INTO tb_learner_no_counters (yy, region_code, last_seq)
SELECT substr(learner_no, 1, 2), region_code, MAX(substr(learner_no, 5, 5)::INTEGER)
  FROM tb_users
 GROUP BY substr(learner_no, 1, 2), region_code;

ALTER TABLE tb_users
    ALTER COLUMN learner_no SET NOT NULL,
    ADD CONSTRAINT uq_tb_users_learner_no UNIQUE (learner_no),
    ADD CONSTRAINT ck_tb_users_learner_no CHECK (learner_no ~ '^[0-9]{9}$');

-- 채번: 들어온 값은 무시하고 항상 매긴다. 카운터 UPSERT 는 행 잠금으로 직렬화되고,
-- 사용자 INSERT 와 같은 트랜잭션이라 가입이 롤백되면 카운터도 롤백된다(결번 허용).
CREATE FUNCTION fn_tb_users_issue_learner_no() RETURNS trigger AS $$
DECLARE
    v_yy CHAR(2) := to_char(NEW.created_at AT TIME ZONE 'Asia/Seoul', 'YY');
    v_seq INTEGER;
BEGIN
    INSERT INTO tb_learner_no_counters (yy, region_code, last_seq)
    VALUES (v_yy, NEW.region_code, 1)
    ON CONFLICT (yy, region_code)
    DO UPDATE SET last_seq = tb_learner_no_counters.last_seq + 1
    RETURNING last_seq INTO v_seq;
    NEW.learner_no := v_yy || NEW.region_code || lpad(v_seq::text, 5, '0');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tb_users_issue_learner_no
    BEFORE INSERT ON tb_users
    FOR EACH ROW EXECUTE FUNCTION fn_tb_users_issue_learner_no();

-- 불변: 한 번 정해진 학번은 바꿀 수 없다. 구·군이 바뀌어도 학번은 그대로다.
CREATE FUNCTION fn_tb_users_keep_learner_no() RETURNS trigger AS $$
BEGIN
    IF NEW.learner_no IS DISTINCT FROM OLD.learner_no THEN
        RAISE EXCEPTION 'learner_no is immutable' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tb_users_keep_learner_no
    BEFORE UPDATE OF learner_no ON tb_users
    FOR EACH ROW EXECUTE FUNCTION fn_tb_users_keep_learner_no();

-- 다대구 첫 로그인의 1회용 가입 토큰. 원문은 저장하지 않고 SHA-256 hex 만 둔다.
CREATE TABLE tb_registration_tokens (
    id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(512) NOT NULL,
    external_did VARCHAR(512),
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    birth_date DATE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_registration_tokens PRIMARY KEY (id),
    CONSTRAINT uq_tb_registration_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_tb_registration_tokens_provider CHECK (provider IN ('DADAEGU'))
);

CREATE INDEX ix_tb_registration_tokens_expires
    ON tb_registration_tokens (expires_at);
