ALTER TABLE tb_blockchain_transactions
    ADD CONSTRAINT ck_tb_blockchain_transactions_operation_reason
    CHECK (
        transaction_type NOT IN ('VC_REVOKE', 'VC_REISSUE')
        OR operation_reason IS NOT NULL
    );
