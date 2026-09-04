ALTER TABLE tb_credentials
    ADD COLUMN chain_key VARCHAR(20),
    ADD COLUMN vc_hash_version VARCHAR(32);

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials_chain_key_format CHECK (
        chain_key IS NULL OR chain_key ~ '^[A-Z0-9]{16}$'
    ),
    ADD CONSTRAINT ck_tb_credentials_vc_hash_version CHECK (
        vc_hash_version IS NULL OR vc_hash_version IN (
            'ENVELOPE_SHA256_V0', 'COMPACT_JWS_SHA256_V1'
        )
    ),
    ADD CONSTRAINT ck_tb_credentials_registry_metadata CHECK (
        (
            chain_key IS NULL
            AND (
                vc_hash_version IS NULL
                OR vc_hash_version = 'ENVELOPE_SHA256_V0'
            )
        )
        OR (
            chain_key IS NOT NULL
            AND vc_hash_version = 'COMPACT_JWS_SHA256_V1'
        )
    );

UPDATE tb_credentials
   SET vc_hash_version = 'ENVELOPE_SHA256_V0'
 WHERE vc_hash IS NOT NULL
   AND vc_hash_version IS NULL;

CREATE UNIQUE INDEX uq_tb_credentials_chain_key
    ON tb_credentials (chain_key)
 WHERE chain_key IS NOT NULL;
