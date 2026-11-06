-- 수료증·학습 배지의 대구체인 NFT 발행 장부 (docs/specs/daeguchain-nft-issuance.md).
-- 대상마다 한 줄. 상태는 체인 처리 단계이고, 폐기 여부는 revoked_at 이 따로 맡는다
-- (NFT 는 지울 수 없으므로 폐기돼도 체인 기록은 남고 메타데이터만 내주지 않는다).
CREATE TABLE tb_nft_issuances (
    id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    contract_address VARCHAR(100),
    metadata_uri VARCHAR(512),
    metadata_json TEXT,
    metadata_hash CHAR(64),
    fact_hash VARCHAR(100),
    token_index BIGINT,
    block_height BIGINT,
    confirmed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_nft_issuances PRIMARY KEY (id),
    CONSTRAINT uq_tb_nft_issuances_target UNIQUE (target_type, target_id),
    CONSTRAINT ck_tb_nft_issuances_target_type CHECK (target_type IN ('CREDENTIAL', 'BADGE')),
    CONSTRAINT ck_tb_nft_issuances_status CHECK (status IN (
        'WAITING_DOMAIN', 'PENDING', 'SUBMITTING', 'SUBMITTED', 'MINTED', 'FAILED', 'UNKNOWN', 'CANCELLED'
    )),
    CONSTRAINT ck_tb_nft_issuances_retry_count CHECK (retry_count >= 0),
    -- 대기 이후로는 체인에 보낼 메타데이터가 정해져 있어야 한다.
    CONSTRAINT ck_tb_nft_issuances_metadata CHECK (
        status IN ('WAITING_DOMAIN', 'CANCELLED')
        OR (metadata_uri IS NOT NULL AND metadata_json IS NOT NULL AND metadata_hash IS NOT NULL)
    ),
    -- 전송 이후로는 영수증(fact hash)이 있어야 한다. 전송 중·결과 모름은 hash 가 없을 수 있다.
    CONSTRAINT ck_tb_nft_issuances_fact_hash CHECK (
        status NOT IN ('SUBMITTED', 'MINTED') OR fact_hash IS NOT NULL
    ),
    CONSTRAINT ck_tb_nft_issuances_minted CHECK (
        status <> 'MINTED' OR (token_index IS NOT NULL AND contract_address IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_tb_nft_issuances_fact_hash
    ON tb_nft_issuances (fact_hash) WHERE fact_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_tb_nft_issuances_token
    ON tb_nft_issuances (contract_address, token_index) WHERE token_index IS NOT NULL;
CREATE INDEX ix_tb_nft_issuances_due
    ON tb_nft_issuances (status, next_attempt_at, created_at);
