-- 기관별 수료증·Status List 서명키를 OpenBao Transit 으로 옮기기 위한 키 마스터·버전 이력·
-- 작업 기록·승인 기록 (ADR-0006, 설계서 §11). 이 마이그레이션은 컬럼 추가까지만 한다.
-- 전환 이전 발급분 백필은 애플리케이션(LegacySignKeyBackfillService)이, CHECK 는 백필 뒤
-- V23 이 건다 (§11.3 순서).

-- 11.1 기관 키 마스터
CREATE TABLE tb_institution_sign_keys (
    id UUID NOT NULL,
    institution_id UUID NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    generation INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    mount VARCHAR(50) NOT NULL,
    key_name VARCHAR(150) NOT NULL,
    key_type VARCHAR(30) NOT NULL DEFAULT 'ed25519',
    current_version INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING',
    status_reason VARCHAR(1000),
    rotated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    compromised_at TIMESTAMPTZ,
    version_no INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_institution_sign_keys PRIMARY KEY (id),
    CONSTRAINT fk_tb_institution_sign_keys_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    -- 버전 테이블의 복합 FK 대상. 버전 행의 institution_id 가 마스터와 어긋나지 못하게 한다.
    CONSTRAINT uq_tb_institution_sign_keys_id_institution UNIQUE (id, institution_id),
    CONSTRAINT uq_tb_institution_sign_keys_generation UNIQUE (institution_id, purpose, generation),
    CONSTRAINT uq_tb_institution_sign_keys_key_name UNIQUE (mount, key_name),
    CONSTRAINT ck_tb_institution_sign_keys_purpose
        CHECK (purpose IN ('CREDENTIAL', 'STATUS_LIST')),
    CONSTRAINT ck_tb_institution_sign_keys_status CHECK (
        status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'REVOKED', 'COMPROMISED', 'ERROR')
    ),
    CONSTRAINT ck_tb_institution_sign_keys_key_type CHECK (key_type IN ('ed25519')),
    CONSTRAINT ck_tb_institution_sign_keys_generation CHECK (generation >= 1),
    CONSTRAINT ck_tb_institution_sign_keys_current_version CHECK (current_version >= 0),
    CONSTRAINT ck_tb_institution_sign_keys_version_no CHECK (version_no >= 1),
    -- Status List 키는 기관이 정지·폐지돼도 계속 쓴다 (§6.1, §9.1).
    CONSTRAINT ck_tb_institution_sign_keys_status_list_purpose CHECK (
        purpose <> 'STATUS_LIST' OR status NOT IN ('SUSPENDED', 'REVOKED')
    ),
    CONSTRAINT ck_tb_institution_sign_keys_suspended_state CHECK (
        status <> 'SUSPENDED' OR suspended_at IS NOT NULL
    ),
    CONSTRAINT ck_tb_institution_sign_keys_revoked_state CHECK (
        status <> 'REVOKED' OR (revoked_at IS NOT NULL AND status_reason IS NOT NULL)
    ),
    -- 마스터 COMPROMISED 는 OpenBao 자체 침해다. 새 세대가 현재 세대가 된다 (§6.5).
    CONSTRAINT ck_tb_institution_sign_keys_compromised_state CHECK (
        status <> 'COMPROMISED' OR (compromised_at IS NOT NULL AND is_current = FALSE)
    )
);

-- 현재 세대는 기관·목적당 하나 (§11.1).
CREATE UNIQUE INDEX uq_tb_institution_sign_keys_current
    ON tb_institution_sign_keys (institution_id, purpose)
 WHERE is_current;

CREATE INDEX ix_tb_institution_sign_keys_institution_status
    ON tb_institution_sign_keys (institution_id, status);

-- 11.2 키 버전 이력
CREATE TABLE tb_institution_sign_key_versions (
    id UUID NOT NULL,
    sign_key_id UUID NOT NULL,
    -- 마스터를 경유하지 않고 기관 단위 kid_fragment 유일성을 걸기 위한 중복 보관 (§11.2).
    institution_id UUID NOT NULL,
    key_version INTEGER NOT NULL,
    kid_fragment VARCHAR(50) NOT NULL,
    public_key_jwk JSONB NOT NULL,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    openbao_created_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ NOT NULL,
    retired_at TIMESTAMPTZ,
    compromised_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_institution_sign_key_versions PRIMARY KEY (id),
    CONSTRAINT fk_tb_institution_sign_key_versions_sign_key
        FOREIGN KEY (sign_key_id, institution_id)
        REFERENCES tb_institution_sign_keys (id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_institution_sign_key_versions_created_by
        FOREIGN KEY (created_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_institution_sign_key_versions_key_version UNIQUE (sign_key_id, key_version),
    -- 세대가 달라도 기관 안에서 kid 가 겹치지 않는다 (§6.2, §11.2).
    CONSTRAINT uq_tb_institution_sign_key_versions_kid_fragment UNIQUE (institution_id, kid_fragment),
    CONSTRAINT ck_tb_institution_sign_key_versions_key_version CHECK (key_version >= 0),
    CONSTRAINT ck_tb_institution_sign_key_versions_status
        CHECK (status IN ('ACTIVE', 'RETIRED', 'COMPROMISED')),
    CONSTRAINT ck_tb_institution_sign_key_versions_source
        CHECK (source IN ('OPENBAO', 'LOCAL_FILE')),
    CONSTRAINT ck_tb_institution_sign_key_versions_kid_fragment_format
        CHECK (kid_fragment ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$'),
    CONSTRAINT ck_tb_institution_sign_key_versions_retired_state CHECK (
        status <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_tb_institution_sign_key_versions_compromised_state CHECK (
        status <> 'COMPROMISED' OR compromised_at IS NOT NULL
    )
);

CREATE INDEX ix_tb_institution_sign_key_versions_sign_key_status
    ON tb_institution_sign_key_versions (sign_key_id, status);

-- COMPROMISED 버전은 어떤 경로로도 해제되지 않는다 (§6.1, §11.2). 상태를 바꾸려는 UPDATE 는
-- 갱신을 건너뛰어 0행으로 끝난다. 다른 컬럼 갱신은 허용한다.
CREATE OR REPLACE FUNCTION fn_tb_institution_sign_key_versions_keep_compromised()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'COMPROMISED' AND NEW.status <> 'COMPROMISED' THEN
        RETURN NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tb_institution_sign_key_versions_keep_compromised
    BEFORE UPDATE ON tb_institution_sign_key_versions
    FOR EACH ROW EXECUTE FUNCTION fn_tb_institution_sign_key_versions_keep_compromised();

-- 11.5 승인 기록. consumed_by_operation_id FK 는 작업 테이블 뒤에 건다.
CREATE TABLE tb_kms_approvals (
    id UUID NOT NULL,
    institution_id UUID NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    action VARCHAR(30) NOT NULL,
    sign_key_id UUID NOT NULL,
    expected_version INTEGER,
    target_key_version INTEGER,
    target_compromised_at TIMESTAMPTZ,
    params_hash CHAR(64) NOT NULL,
    approved_by UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    consumed_by_operation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_kms_approvals PRIMARY KEY (id),
    CONSTRAINT fk_tb_kms_approvals_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_kms_approvals_sign_key_id
        FOREIGN KEY (sign_key_id) REFERENCES tb_institution_sign_keys (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_kms_approvals_approved_by
        FOREIGN KEY (approved_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_kms_approvals_purpose CHECK (purpose IN ('CREDENTIAL', 'STATUS_LIST')),
    CONSTRAINT ck_tb_kms_approvals_action CHECK (
        action IN ('CREATE', 'ROTATE', 'SUSPEND', 'RESUME', 'REVOKE', 'COMPROMISE')
    ),
    CONSTRAINT ck_tb_kms_approvals_params_hash CHECK (params_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tb_kms_approvals_consumed_state CHECK (
        (consumed_at IS NULL) = (consumed_by_operation_id IS NULL)
    )
);

CREATE INDEX ix_tb_kms_approvals_sign_key_expires
    ON tb_kms_approvals (sign_key_id, expires_at);

-- 11.4 작업 기록
CREATE TABLE tb_kms_operations (
    id UUID NOT NULL,
    sign_key_id UUID NOT NULL,
    institution_id UUID NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    action VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARED',
    target_key_version INTEGER,
    expected_version_before INTEGER,
    observed_version_after INTEGER,
    prepared_at TIMESTAMPTZ NOT NULL,
    last_call_at TIMESTAMPTZ,
    owner VARCHAR(100),
    lease_until TIMESTAMPTZ,
    before_value JSONB,
    after_value JSONB,
    reason VARCHAR(1000),
    requested_by UUID,
    approval_id UUID,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_kms_operations PRIMARY KEY (id),
    CONSTRAINT fk_tb_kms_operations_sign_key_id
        FOREIGN KEY (sign_key_id) REFERENCES tb_institution_sign_keys (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_kms_operations_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_kms_operations_requested_by
        FOREIGN KEY (requested_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_kms_operations_approval_id
        FOREIGN KEY (approval_id) REFERENCES tb_kms_approvals (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_kms_operations_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_tb_kms_operations_purpose CHECK (purpose IN ('CREDENTIAL', 'STATUS_LIST')),
    CONSTRAINT ck_tb_kms_operations_action CHECK (
        action IN ('CREATE', 'ROTATE', 'SUSPEND', 'RESUME', 'REVOKE', 'COMPROMISE')
    ),
    CONSTRAINT ck_tb_kms_operations_status CHECK (
        status IN ('PREPARED', 'UNCONFIRMED', 'RECORDED', 'COMPLETED', 'FAILED')
    ),
    -- 소유권은 owner 와 lease_until 이 함께 있거나 함께 없다.
    CONSTRAINT ck_tb_kms_operations_owner_state CHECK ((owner IS NULL) = (lease_until IS NULL))
);

-- 같은 키에 진행 중 작업은 하나만 (§11.4). sign_key_id 가 NOT NULL 이라 CREATE 도 예외가 없다.
CREATE UNIQUE INDEX uq_tb_kms_operations_in_progress
    ON tb_kms_operations (sign_key_id)
 WHERE status IN ('PREPARED', 'UNCONFIRMED', 'RECORDED');

CREATE INDEX ix_tb_kms_operations_status_prepared
    ON tb_kms_operations (status, prepared_at);

ALTER TABLE tb_kms_approvals
    ADD CONSTRAINT fk_tb_kms_approvals_consumed_by_operation_id
    FOREIGN KEY (consumed_by_operation_id) REFERENCES tb_kms_operations (id) ON DELETE RESTRICT;

-- 11.3 1번: 기존 테이블 확장. NULL 허용. 백필과 CHECK(V23) 는 뒤에 (계획 7).
ALTER TABLE tb_credentials
    ADD COLUMN sign_key_id UUID,
    ADD COLUMN sign_key_version INTEGER,
    ADD COLUMN vc_jws_header TEXT,
    ADD COLUMN vc_jws_payload TEXT,
    ADD COLUMN sign_attempt_no INTEGER;

ALTER TABLE tb_credentials
    ADD CONSTRAINT fk_tb_credentials_sign_key_id
    FOREIGN KEY (sign_key_id) REFERENCES tb_institution_sign_keys (id) ON DELETE RESTRICT;

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials_sign_key_pair CHECK (
        (sign_key_id IS NULL) = (sign_key_version IS NULL)
        AND (sign_key_version IS NULL OR sign_key_version >= 0)
        AND (sign_attempt_no IS NULL OR sign_attempt_no >= 1)
    );

CREATE INDEX ix_tb_credentials_sign_key
    ON tb_credentials (sign_key_id, sign_key_version);

ALTER TABLE tb_credential_status_lists
    ADD COLUMN sign_key_id UUID,
    ADD COLUMN sign_key_version INTEGER;

ALTER TABLE tb_credential_status_lists
    ADD CONSTRAINT fk_tb_credential_status_lists_sign_key_id
    FOREIGN KEY (sign_key_id) REFERENCES tb_institution_sign_keys (id) ON DELETE RESTRICT;

ALTER TABLE tb_credential_status_lists
    ADD CONSTRAINT ck_tb_credential_status_lists_sign_key_pair CHECK (
        (sign_key_id IS NULL) = (sign_key_version IS NULL)
        AND (sign_key_version IS NULL OR sign_key_version >= 0)
    );
