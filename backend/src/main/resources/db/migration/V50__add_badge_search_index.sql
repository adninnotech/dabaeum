-- 배지 단계·유효성 리뷰 후속 (설계서 §11.4).
-- 1) 관리자 배지 현황 검색(정의·상태 필터, 발급 최신순)과 definition_id FK 를 받치는 인덱스.
--    기존 uq_tb_learning_badges_user_definition 은 user_id 가 앞이라 정의만으로 거르는 조회에 못 쓴다.
-- 2) 만료일 CHECK 를 조인다. 만료일은 발급된 배지에만 있고 발급일보다 뒤여야 한다(지금은 모두 비어 있다).

CREATE INDEX IF NOT EXISTS ix_tb_learning_badges_search
    ON tb_learning_badges (definition_id, status, issued_at DESC)
 WHERE status IN ('ISSUED', 'REVOKED');

ALTER TABLE tb_learning_badges DROP CONSTRAINT ck_tb_learning_badges_expires_at;
ALTER TABLE tb_learning_badges
    ADD CONSTRAINT ck_tb_learning_badges_expires_at
        CHECK (expires_at IS NULL OR (issued_at IS NOT NULL AND expires_at > issued_at));
