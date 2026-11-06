-- 약관 동의 이력. 개인정보 관련이라 언제 무엇에 동의했는지 남긴다.
-- 동의를 철회·재동의할 수 있으므로 한 사용자가 같은 타입의 행을 여러 개 갖는다.
CREATE TABLE tb_user_agreements (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    agreement_type VARCHAR(30) NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_user_agreements PRIMARY KEY (id),
    CONSTRAINT fk_tb_user_agreements_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_user_agreements_type
        CHECK (agreement_type IN ('USAGE', 'PRIVACY', 'MARKETING'))
);

CREATE INDEX ix_tb_user_agreements_user
    ON tb_user_agreements (user_id, agreement_type, agreed_at DESC);
