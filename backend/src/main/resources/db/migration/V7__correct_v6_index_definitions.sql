DROP INDEX ix_tb_credential_verifications__credential_verified_at;

CREATE INDEX ix_tb_credential_verifications__credential_verified_at
    ON tb_credential_verifications (credential_id, verified_at DESC);

DROP INDEX ix_tb_external_mappings__internal;

CREATE INDEX ix_tb_external_mappings__internal
    ON tb_external_mappings (resource_type, internal_id);
