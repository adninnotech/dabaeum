CREATE TABLE tb_credential_groups (
    id UUID NOT NULL,
    completion_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_credential_groups PRIMARY KEY (id),
    CONSTRAINT fk_tb_credential_groups_completion_id
        FOREIGN KEY (completion_id) REFERENCES tb_completions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_credential_groups_completion_id UNIQUE (completion_id)
);

CREATE TABLE tb_credentials (
    id UUID NOT NULL,
    credential_group_id UUID NOT NULL,
    previous_credential_id UUID,
    credential_no VARCHAR(100) NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    issuer_did VARCHAR(512),
    subject_did VARCHAR(512),
    credential_type VARCHAR(100) NOT NULL DEFAULT 'LIFELONG_EDUCATION_COMPLETION',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    vc_payload JSONB,
    vc_hash CHAR(64),
    issued_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(1000),
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_credentials PRIMARY KEY (id),
    CONSTRAINT fk_tb_credentials_credential_group_id
        FOREIGN KEY (credential_group_id)
        REFERENCES tb_credential_groups (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_tb_credentials_credential_no UNIQUE (credential_no),
    CONSTRAINT uq_tb_credentials_group_version UNIQUE (credential_group_id, version_no),
    CONSTRAINT ck_tb_credentials_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_tb_credentials_status CHECK (
        status IN ('PENDING', 'ISSUING', 'ISSUED', 'FAILED', 'REVOKED', 'SUPERSEDED', 'EXPIRED')
    ),
    CONSTRAINT ck_tb_credentials_valid_dates CHECK (
        valid_until IS NULL OR valid_from < valid_until
    ),
    CONSTRAINT ck_tb_credentials_issued_fields CHECK (
        status <> 'ISSUED'
        OR (issued_at IS NOT NULL AND valid_from IS NOT NULL AND vc_hash IS NOT NULL)
    ),
    CONSTRAINT ck_tb_credentials_failed_failure_code CHECK (
        status <> 'FAILED' OR failure_code IS NOT NULL
    ),
    CONSTRAINT ck_tb_credentials_revoked_fields CHECK (
        status <> 'REVOKED'
        OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
    )
);

ALTER TABLE tb_credentials
    ADD CONSTRAINT uq_tb_credentials__id_group
    UNIQUE (id, credential_group_id);

ALTER TABLE tb_credentials
    ADD CONSTRAINT fk_tb_credentials__previous_same_group
    FOREIGN KEY (previous_credential_id, credential_group_id)
    REFERENCES tb_credentials (id, credential_group_id)
    ON DELETE RESTRICT;

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials__previous_not_self
    CHECK (previous_credential_id IS NULL OR previous_credential_id <> id);

CREATE UNIQUE INDEX uq_tb_credentials_active_issued
    ON tb_credentials (credential_group_id)
 WHERE status = 'ISSUED';

CREATE TABLE tb_learning_badges (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    course_id UUID,
    credential_id UUID,
    badge_type VARCHAR(50) NOT NULL,
    badge_name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    nft_token_id VARCHAR(255),
    metadata JSONB,
    issued_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_learning_badges PRIMARY KEY (id),
    CONSTRAINT fk_tb_learning_badges_user_id
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_learning_badges_course_id
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_learning_badges_credential_id
        FOREIGN KEY (credential_id) REFERENCES tb_credentials (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_learning_badges_status CHECK (
        status IN ('PENDING', 'ISSUED', 'FAILED', 'REVOKED')
    )
);

CREATE UNIQUE INDEX uq_tb_learning_badges_nft_token
    ON tb_learning_badges (nft_token_id)
 WHERE nft_token_id IS NOT NULL;

CREATE TABLE tb_credential_verifications (
    id UUID NOT NULL,
    credential_id UUID,
    presented_credential_no VARCHAR(100),
    presented_hash CHAR(64),
    verification_type VARCHAR(30) NOT NULL,
    requester_type VARCHAR(30) NOT NULL,
    requester_id VARCHAR(255),
    result VARCHAR(30) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_ip INET,
    verification_hash CHAR(64),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_credential_verifications PRIMARY KEY (id),
    CONSTRAINT fk_tb_credential_verifications_credential_id
        FOREIGN KEY (credential_id) REFERENCES tb_credentials (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_credential_verifications_type CHECK (
        verification_type IN ('QR', 'API', 'ADMIN')
    ),
    CONSTRAINT ck_tb_credential_verifications_requester_type CHECK (
        requester_type IN ('INDIVIDUAL', 'INSTITUTION', 'EXTERNAL_ORGANIZATION', 'SYSTEM')
    ),
    CONSTRAINT ck_tb_credential_verifications_result CHECK (
        result IN ('VALID', 'INVALID', 'REVOKED', 'SUPERSEDED', 'EXPIRED', 'NOT_FOUND', 'ERROR')
    ),
    CONSTRAINT ck_tb_credential_verifications_identifier CHECK (
        credential_id IS NOT NULL
        OR presented_credential_no IS NOT NULL
        OR presented_hash IS NOT NULL
    ),
    CONSTRAINT ck_tb_credential_verifications_result_credential CHECK (
        (result = 'NOT_FOUND' AND credential_id IS NULL)
        OR (result <> 'NOT_FOUND' AND credential_id IS NOT NULL)
    )
);

CREATE TABLE tb_blockchain_transactions (
    id UUID NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    network VARCHAR(50) NOT NULL DEFAULT 'DAEGUCHAIN',
    transaction_type VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    correlation_id UUID,
    request_hash CHAR(64),
    transaction_id VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    response_metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_blockchain_transactions PRIMARY KEY (id),
    CONSTRAINT uq_tb_blockchain_transactions_network_idempotency_key
        UNIQUE (network, idempotency_key),
    CONSTRAINT ck_tb_blockchain_transactions_reference_type CHECK (
        reference_type IN ('CREDENTIAL', 'BADGE', 'USER_IDENTITY')
    ),
    CONSTRAINT ck_tb_blockchain_transactions_type CHECK (
        transaction_type IN ('VC_ANCHOR', 'VC_REVOKE', 'NFT_ISSUE', 'DID_REGISTER')
    ),
    CONSTRAINT ck_tb_blockchain_transactions_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'FAILED')
    ),
    CONSTRAINT ck_tb_blockchain_transactions_retry_count CHECK (retry_count >= 0)
);

CREATE UNIQUE INDEX uq_tb_blockchain_transactions_network_transaction
    ON tb_blockchain_transactions (network, transaction_id)
 WHERE transaction_id IS NOT NULL;
