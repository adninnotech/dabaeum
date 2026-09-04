ALTER TABLE tb_credentials
    RENAME COLUMN issuer_did TO issuer_identifier;

ALTER TABLE tb_credentials
    RENAME COLUMN subject_did TO subject_identifier;

ALTER TABLE tb_credentials
    DROP CONSTRAINT ck_tb_credentials_issued_fields;

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials_issued_fields CHECK (
        status NOT IN ('ISSUED', 'SUPERSEDED', 'REVOKED')
        OR (
            vc_payload IS NOT NULL
            AND vc_hash IS NOT NULL
            AND issuer_identifier IS NOT NULL
            AND subject_identifier IS NOT NULL
            AND valid_from IS NOT NULL
            AND issued_at IS NOT NULL
        )
    );

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials_vc_hash_format CHECK (
        vc_hash IS NULL OR vc_hash ~ '^[0-9a-f]{64}$'
    );

ALTER TABLE tb_blockchain_transactions
    ALTER COLUMN network SET DEFAULT 'DABAEUM_FABRIC';

ALTER TABLE tb_blockchain_transactions
    DROP CONSTRAINT ck_tb_blockchain_transactions_type;

ALTER TABLE tb_blockchain_transactions
    ADD CONSTRAINT ck_tb_blockchain_transactions_type CHECK (
        transaction_type IN ('VC_ANCHOR', 'VC_REVOKE', 'VC_REISSUE', 'NFT_ISSUE', 'DID_REGISTER')
    );
