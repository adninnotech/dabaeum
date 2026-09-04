ALTER TABLE tb_credentials
    DROP CONSTRAINT ck_tb_credentials_issued_fields;

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials_issued_fields CHECK (
        status NOT IN ('ISSUED', 'SUPERSEDED', 'REVOKED', 'EXPIRED')
        OR (
            vc_payload IS NOT NULL
            AND vc_hash IS NOT NULL
            AND issuer_identifier IS NOT NULL
            AND subject_identifier IS NOT NULL
            AND valid_from IS NOT NULL
            AND issued_at IS NOT NULL
        )
    );
