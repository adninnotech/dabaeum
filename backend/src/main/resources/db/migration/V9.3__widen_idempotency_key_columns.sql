ALTER TABLE tb_blockchain_transactions
    ALTER COLUMN idempotency_key TYPE VARCHAR(128);

ALTER TABLE tb_idempotency_requests
    ALTER COLUMN idempotency_key TYPE VARCHAR(128);
