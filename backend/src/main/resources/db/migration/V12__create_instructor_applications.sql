CREATE TABLE tb_instructor_applications (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    institution_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    application_message VARCHAR(1000),
    rejection_reason VARCHAR(1000),
    reviewed_by UUID,
    applied_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_instructor_applications PRIMARY KEY (id),
    CONSTRAINT fk_tb_instructor_applications_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_instructor_applications_institution
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_instructor_applications_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_instructor_applications_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_tb_instructor_applications_message CHECK (
        application_message IS NULL
        OR char_length(application_message) BETWEEN 1 AND 1000
    ),
    CONSTRAINT ck_tb_instructor_applications_rejection_reason CHECK (
        rejection_reason IS NULL
        OR char_length(rejection_reason) BETWEEN 1 AND 1000
    ),
    CONSTRAINT ck_tb_instructor_applications_state CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL
            AND reviewed_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'APPROVED' AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_tb_instructor_applications_pending
    ON tb_instructor_applications (user_id, institution_id)
    WHERE status = 'PENDING';

CREATE INDEX ix_tb_instructor_applications_institution_status_applied
    ON tb_instructor_applications (institution_id, status, applied_at DESC);

CREATE INDEX ix_tb_instructor_applications_user_applied
    ON tb_instructor_applications (user_id, applied_at DESC);
