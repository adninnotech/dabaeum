ALTER INDEX uq_tb_institutions_institution_code_active
    RENAME TO ix_tb_institutions_institution_code_active;

ALTER INDEX uq_tb_user_identities_external_did
    RENAME TO ix_tb_user_identities_external_did;

ALTER INDEX uq_tb_user_roles_institution_role
    RENAME TO ix_tb_user_roles_institution_role;

ALTER INDEX uq_tb_user_roles_platform_role
    RENAME TO ix_tb_user_roles_platform_role;
