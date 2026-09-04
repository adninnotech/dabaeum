CREATE TABLE tb_external_systems (
    id UUID NOT NULL,
    institution_id UUID,
    system_code VARCHAR(50) NOT NULL,
    system_name VARCHAR(200) NOT NULL,
    system_type VARCHAR(50) NOT NULL,
    base_url VARCHAR(1000),
    auth_type VARCHAR(30),
    secret_ref VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_external_systems PRIMARY KEY (id),
    CONSTRAINT fk_tb_external_systems_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_external_systems_system_code UNIQUE (system_code),
    CONSTRAINT ck_tb_external_systems_system_type CHECK (
        system_type IN ('DADAEGU', 'D_DATAHUB', 'INSTITUTION_LMS', 'DAEGUCHAIN')
    ),
    CONSTRAINT ck_tb_external_systems_auth_type CHECK (
        auth_type IS NULL OR auth_type IN ('NONE', 'API_KEY', 'OAUTH2', 'MTLS')
    ),
    CONSTRAINT ck_tb_external_systems_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'ERROR')
    )
);

CREATE TABLE tb_external_mappings (
    id UUID NOT NULL,
    external_system_id UUID NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    internal_id UUID NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_external_mappings PRIMARY KEY (id),
    CONSTRAINT fk_tb_external_mappings_external_system_id
        FOREIGN KEY (external_system_id)
        REFERENCES tb_external_systems (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_tb_external_mappings_external_id
        UNIQUE (external_system_id, resource_type, external_id),
    CONSTRAINT uq_tb_external_mappings_internal_id
        UNIQUE (external_system_id, resource_type, internal_id),
    CONSTRAINT ck_tb_external_mappings_resource_type CHECK (
        resource_type IN ('INSTITUTION', 'USER', 'COURSE', 'SESSION', 'ENROLLMENT')
    )
);

CREATE TABLE tb_webhook_subscriptions (
    id UUID NOT NULL,
    external_system_id UUID NOT NULL,
    callback_url VARCHAR(1000) NOT NULL,
    event_types TEXT[] NOT NULL,
    secret_ref VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_webhook_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_tb_webhook_subscriptions_external_system_id
        FOREIGN KEY (external_system_id)
        REFERENCES tb_external_systems (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_tb_webhook_subscriptions_status CHECK (
        status IN ('ACTIVE', 'INACTIVE')
    )
);

CREATE TABLE tb_outbox_events (
    id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_tb_outbox_events_status CHECK (
        status IN ('PENDING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_tb_outbox_events_retry_count CHECK (retry_count >= 0)
);

CREATE TABLE tb_webhook_deliveries (
    id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    outbox_event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    http_status INTEGER,
    response_excerpt VARCHAR(2000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_webhook_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_tb_webhook_deliveries_subscription_id
        FOREIGN KEY (subscription_id)
        REFERENCES tb_webhook_subscriptions (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tb_webhook_deliveries_outbox_event_id
        FOREIGN KEY (outbox_event_id)
        REFERENCES tb_outbox_events (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_tb_webhook_deliveries_subscription_outbox
        UNIQUE (subscription_id, outbox_event_id),
    CONSTRAINT ck_tb_webhook_deliveries_status CHECK (
        status IN ('PENDING', 'SENT', 'FAILED')
    ),
    CONSTRAINT ck_tb_webhook_deliveries_retry_count CHECK (retry_count >= 0)
);

CREATE TABLE tb_idempotency_requests (
    id UUID NOT NULL,
    scope VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    http_status INTEGER,
    response_body JSONB,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_idempotency_requests PRIMARY KEY (id),
    CONSTRAINT uq_tb_idempotency_requests_scope_key UNIQUE (scope, idempotency_key),
    CONSTRAINT ck_tb_idempotency_requests_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'FAILED')
    )
);

CREATE TABLE tb_audit_logs (
    id UUID NOT NULL,
    actor_id UUID,
    actor_role VARCHAR(30),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID NOT NULL,
    before_data JSONB,
    after_data JSONB,
    ip_address INET,
    request_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_audit_logs PRIMARY KEY (id)
);
