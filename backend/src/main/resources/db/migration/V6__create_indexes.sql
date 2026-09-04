CREATE INDEX ix_tb_courses__institution_status
    ON tb_courses (institution_id, status);

CREATE INDEX ix_tb_course_sessions__course_starts_at
    ON tb_course_sessions (course_id, starts_at);

CREATE INDEX ix_tb_enrollments__user_status
    ON tb_enrollments (user_id, status);

CREATE INDEX ix_tb_attendance_records__enrollment
    ON tb_attendance_records (enrollment_id);

CREATE INDEX ix_tb_completions__status
    ON tb_completions (status);

CREATE INDEX ix_tb_credential_verifications__credential_verified_at
    ON tb_credential_verifications (credential_id, verified_at);

CREATE INDEX ix_tb_blockchain_transactions__status_next_retry
    ON tb_blockchain_transactions (status, next_retry_at);

CREATE INDEX ix_tb_external_mappings__internal
    ON tb_external_mappings (internal_id);

CREATE INDEX ix_tb_outbox_events__status_next_retry
    ON tb_outbox_events (status, next_retry_at);

CREATE INDEX ix_tb_webhook_deliveries__status_next_retry
    ON tb_webhook_deliveries (status, next_retry_at);

CREATE INDEX ix_tb_idempotency_requests__expires_at
    ON tb_idempotency_requests (expires_at);

CREATE INDEX ix_tb_audit_logs__resource_created_at
    ON tb_audit_logs (resource_type, resource_id, created_at);
