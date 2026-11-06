-- 학습배지 단계·유효성 관리 (설계서 §11.4, 발주처 자료 2026-09-29).
-- 단계 규칙 TIERED, 배지 단계·만료일, 변동 이력(추가 전용), 평생학습 이수 단계 정의 시드와 기존 수료자 백필.

ALTER TABLE tb_badge_definitions DROP CONSTRAINT ck_tb_badge_definitions_rule;
ALTER TABLE tb_badge_definitions
    ADD CONSTRAINT ck_tb_badge_definitions_rule CHECK (rule_type IN ('COURSE_COMPLETION', 'CUMULATIVE', 'TIERED'));

-- 만료일은 정책이 정해지기 전까지 비운다.
ALTER TABLE tb_learning_badges
    ADD COLUMN tier_level SMALLINT,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_tb_learning_badges_tier_level CHECK (tier_level IS NULL OR tier_level BETWEEN 1 AND 10),
    ADD CONSTRAINT ck_tb_learning_badges_expires_at CHECK (expires_at IS NULL OR issued_at IS NULL OR expires_at > issued_at);

-- 발급·갱신(단계 상승·하강)·취소·복구를 사유와 함께 남긴다. 고치지 않는다(배지와 함께만 지워진다).
CREATE TABLE tb_learning_badge_events (
    id          UUID         NOT NULL,
    badge_id    UUID         NOT NULL,
    event_type  VARCHAR(20)  NOT NULL,
    from_level  SMALLINT,
    to_level    SMALLINT,
    reason      VARCHAR(200) NOT NULL,
    actor_id    UUID,
    occurred_at TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_learning_badge_events PRIMARY KEY (id),
    CONSTRAINT fk_tb_learning_badge_events_badge
        FOREIGN KEY (badge_id) REFERENCES tb_learning_badges (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_learning_badge_events_type CHECK (event_type IN ('ISSUED', 'RENEWED', 'REVOKED', 'RESTORED')),
    CONSTRAINT ck_tb_learning_badge_events_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_tb_learning_badge_events_badge ON tb_learning_badge_events (badge_id, occurred_at DESC);

CREATE FUNCTION fn_tb_learning_badge_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'tb_learning_badge_events is append-only'
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tb_learning_badge_events_append_only
    BEFORE UPDATE ON tb_learning_badge_events
    FOR EACH ROW EXECUTE FUNCTION fn_tb_learning_badge_events_append_only();

-- 기존 배지의 이력: 발급(ISSUED 또는 이미 폐기된 것의 최초 발급)과 폐기.
INSERT INTO tb_learning_badge_events (id, badge_id, event_type, reason, occurred_at)
SELECT gen_random_uuid(), id, 'ISSUED', '기존 발급 이관', COALESCE(issued_at, created_at)
  FROM tb_learning_badges
 WHERE status IN ('ISSUED', 'REVOKED');

INSERT INTO tb_learning_badge_events (id, badge_id, event_type, reason, occurred_at)
SELECT gen_random_uuid(), id, 'REVOKED', '기존 폐기 이관', COALESCE(revoked_at, updated_at)
  FROM tb_learning_badges
 WHERE status = 'REVOKED';

-- 평생학습 이수 단계 정의(발주처 발급 체계안). 기준표는 발급 전까지 관리 화면에서 바꿀 수 있다.
INSERT INTO tb_badge_definitions (id, code, name, description, rule_type, rule_params, active)
VALUES (
    gen_random_uuid(), 'LIFELONG_LEARNING_TIER', '평생학습 이수 단계',
    '강좌 이수 건수에 따라 씨앗부터 나눔까지 7단계로 올라가는 배지',
    'TIERED',
    '{"levels": [
        {"level": 1, "name": "씨앗", "minCount": 1},
        {"level": 2, "name": "새싹", "minCount": 6},
        {"level": 3, "name": "자람", "minCount": 11},
        {"level": 4, "name": "이음", "minCount": 16},
        {"level": 5, "name": "펼침", "minCount": 21},
        {"level": 6, "name": "빛남", "minCount": 31},
        {"level": 7, "name": "나눔", "minCount": 41}
    ]}'::jsonb,
    TRUE
);

-- 기존 수료자에게 현재 단계 배지를 준다. 이수 건수 기준은 위 시드와 같다.
CREATE TEMPORARY TABLE tmp_tier_backfill ON COMMIT DROP AS
SELECT gen_random_uuid() AS badge_id, counted.user_id,
       CASE
           WHEN counted.completed >= 41 THEN 7
           WHEN counted.completed >= 31 THEN 6
           WHEN counted.completed >= 21 THEN 5
           WHEN counted.completed >= 16 THEN 4
           WHEN counted.completed >= 11 THEN 3
           WHEN counted.completed >= 6 THEN 2
           ELSE 1
       END AS tier_level,
       counted.completed
  FROM (
      SELECT e.user_id, COUNT(*) AS completed
        FROM tb_completions cp
        JOIN tb_enrollments e ON e.id = cp.enrollment_id
       WHERE cp.status = 'COMPLETED'
       GROUP BY e.user_id
  ) counted;

INSERT INTO tb_learning_badges (id, user_id, badge_type, badge_name, status, issued_at, created_at, updated_at,
                                definition_id, tier_level)
SELECT t.badge_id, t.user_id, 'TIERED', d.name, 'ISSUED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       d.id, t.tier_level
  FROM tmp_tier_backfill t
  CROSS JOIN tb_badge_definitions d
 WHERE d.code = 'LIFELONG_LEARNING_TIER';

INSERT INTO tb_learning_badge_events (id, badge_id, event_type, to_level, reason, occurred_at)
SELECT gen_random_uuid(), t.badge_id, 'ISSUED', t.tier_level, '기존 강좌 이수 ' || t.completed || '건 반영',
       CURRENT_TIMESTAMP
  FROM tmp_tier_backfill t;
