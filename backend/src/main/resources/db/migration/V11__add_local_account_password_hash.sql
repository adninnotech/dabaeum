ALTER TABLE tb_user_identities
    ADD COLUMN password_hash VARCHAR(100);

ALTER TABLE tb_user_identities
    ADD CONSTRAINT ck_tb_user_identities_password_provider
    CHECK (provider = 'LOCAL' OR password_hash IS NULL);
