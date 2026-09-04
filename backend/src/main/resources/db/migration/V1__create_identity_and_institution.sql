CREATE TABLE tb_institutions (
    id UUID NOT NULL,
    institution_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    business_number VARCHAR(20),
    representative_name VARCHAR(100),
    address TEXT,
    contact_phone VARCHAR(30),
    contact_email VARCHAR(320),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_tb_institutions PRIMARY KEY (id),
    CONSTRAINT ck_tb_institutions_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX uq_tb_institutions_institution_code_active
    ON tb_institutions (institution_code)
    WHERE deleted_at IS NULL;

CREATE TABLE tb_users (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(30),
    birth_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    withdrawn_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_users PRIMARY KEY (id),
    CONSTRAINT ck_tb_users_status
        CHECK (status IN ('ACTIVE', 'DORMANT', 'WITHDRAWN', 'SUSPENDED'))
);

CREATE TABLE tb_user_identities (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    external_did VARCHAR(512),
    verified_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_user_identities PRIMARY KEY (id),
    CONSTRAINT fk_tb_user_identities_user_id
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_user_identities_provider_subject
        UNIQUE (provider, provider_subject),
    CONSTRAINT ck_tb_user_identities_provider
        CHECK (provider IN ('LOCAL', 'DADAEGU', 'DID'))
);

CREATE UNIQUE INDEX uq_tb_user_identities_external_did
    ON tb_user_identities (external_did)
    WHERE external_did IS NOT NULL;

CREATE TABLE tb_user_roles (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    institution_id UUID,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_user_roles PRIMARY KEY (id),
    CONSTRAINT fk_tb_user_roles_user_id
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_user_roles_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_user_roles_role
        CHECK (role IN (
            'LEARNER',
            'INSTITUTION_ADMIN',
            'INSTRUCTOR',
            'PLATFORM_ADMIN'
        ))
);

CREATE UNIQUE INDEX uq_tb_user_roles_institution_role
    ON tb_user_roles (user_id, institution_id, role)
    WHERE institution_id IS NOT NULL;

CREATE UNIQUE INDEX uq_tb_user_roles_platform_role
    ON tb_user_roles (user_id, role)
    WHERE institution_id IS NULL;
