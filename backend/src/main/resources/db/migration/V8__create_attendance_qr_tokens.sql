DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM tb_attendance_records
         WHERE attendance_method = 'QR'
    ) THEN
        RAISE EXCEPTION 'V8 requires reviewed remediation for existing QR attendance rows';
    END IF;
END $$;

CREATE TABLE tb_attendance_qr_tokens (
    id UUID NOT NULL,
    session_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_by UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_attendance_qr_tokens PRIMARY KEY (id),
    CONSTRAINT fk_tb_attendance_qr_tokens_session_id
        FOREIGN KEY (session_id) REFERENCES tb_course_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_attendance_qr_tokens_issued_by
        FOREIGN KEY (issued_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_attendance_qr_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_tb_attendance_qr_tokens_token_hash CHECK (
        token_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_tb_attendance_qr_tokens_time CHECK (issued_at < expires_at),
    CONSTRAINT ck_tb_attendance_qr_tokens_revoked_at CHECK (
        revoked_at IS NULL OR revoked_at >= issued_at
    )
);

ALTER TABLE tb_attendance_records ADD COLUMN qr_token_id UUID;

ALTER TABLE tb_attendance_records
    ADD CONSTRAINT fk_tb_attendance_records_qr_token_id
    FOREIGN KEY (qr_token_id) REFERENCES tb_attendance_qr_tokens (id) ON DELETE RESTRICT;

ALTER TABLE tb_attendance_records
    ADD CONSTRAINT ck_tb_attendance_records_qr_token_pair CHECK (
        (attendance_method = 'QR' AND qr_token_id IS NOT NULL)
        OR (attendance_method <> 'QR' AND qr_token_id IS NULL)
    );

CREATE INDEX ix_tb_attendance_qr_tokens_session_expires
    ON tb_attendance_qr_tokens (session_id, expires_at DESC);

CREATE UNIQUE INDEX uq_tb_attendance_qr_tokens_unrevoked_session
    ON tb_attendance_qr_tokens (session_id)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_tb_attendance_adjustments_record_adjusted
    ON tb_attendance_adjustments (attendance_record_id, adjusted_at);
