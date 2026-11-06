-- 평생학습플랫폼 고도화 계획 7 — 학습포트폴리오 공유 링크 (설계서 §6.2)
-- 토큰 원문은 저장하지 않는다(SHA-256 hex 만). 고른 수료증·배지 id 를 배열로 담는다.

CREATE TABLE tb_portfolio_shares (
    id                      UUID        NOT NULL,
    user_id                 UUID        NOT NULL,
    token_hash              CHAR(64)    NOT NULL,
    included_credential_ids UUID[]      NOT NULL,
    included_badge_ids      UUID[]      NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL,
    revoked_at              TIMESTAMPTZ,
    view_count              INTEGER     NOT NULL DEFAULT 0,
    last_viewed_at          TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_portfolio_shares PRIMARY KEY (id),
    CONSTRAINT uq_tb_portfolio_shares_hash UNIQUE (token_hash),
    CONSTRAINT fk_tb_portfolio_shares_user FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_portfolio_shares_view_count CHECK (view_count >= 0)
);

CREATE INDEX ix_tb_portfolio_shares_user ON tb_portfolio_shares (user_id, created_at DESC);
