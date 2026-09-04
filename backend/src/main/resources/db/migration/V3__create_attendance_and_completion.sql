CREATE TABLE tb_attendance_records (
    id UUID NOT NULL,
    course_id UUID NOT NULL,
    session_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    attendance_method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    checked_at TIMESTAMPTZ,
    source VARCHAR(30) NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_attendance_records PRIMARY KEY (id),
    CONSTRAINT fk_tb_attendance_records__session
        FOREIGN KEY (session_id, course_id)
        REFERENCES tb_course_sessions (id, course_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tb_attendance_records__enrollment
        FOREIGN KEY (enrollment_id, course_id)
        REFERENCES tb_enrollments (id, course_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tb_attendance_records_created_by
        FOREIGN KEY (created_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_attendance_records_session_enrollment
        UNIQUE (session_id, enrollment_id),
    CONSTRAINT ck_tb_attendance_records_method CHECK (
        attendance_method IN ('QR', 'ADMIN', 'EXTERNAL')
    ),
    CONSTRAINT ck_tb_attendance_records_status CHECK (
        status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')
    ),
    CONSTRAINT ck_tb_attendance_records_source CHECK (
        source IN ('APP', 'ADMIN_WEB', 'EXTERNAL_API')
    )
);

CREATE TABLE tb_attendance_adjustments (
    id UUID NOT NULL,
    attendance_record_id UUID NOT NULL,
    before_status VARCHAR(30) NOT NULL,
    after_status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    adjusted_by UUID NOT NULL,
    adjusted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_attendance_adjustments PRIMARY KEY (id),
    CONSTRAINT fk_tb_attendance_adjustments_attendance_record_id
        FOREIGN KEY (attendance_record_id)
        REFERENCES tb_attendance_records (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tb_attendance_adjustments_adjusted_by
        FOREIGN KEY (adjusted_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_attendance_adjustments_before_status CHECK (
        before_status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')
    ),
    CONSTRAINT ck_tb_attendance_adjustments_after_status CHECK (
        after_status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')
    )
);

CREATE TABLE tb_completions (
    id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_EVALUATION',
    attendance_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    completed_minutes INTEGER NOT NULL DEFAULT 0,
    credit_value NUMERIC(5, 2),
    evaluated_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_completions PRIMARY KEY (id),
    CONSTRAINT fk_tb_completions_enrollment_id
        FOREIGN KEY (enrollment_id) REFERENCES tb_enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_completions_confirmed_by
        FOREIGN KEY (confirmed_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_completions_enrollment_id UNIQUE (enrollment_id),
    CONSTRAINT ck_tb_completions_status CHECK (
        status IN (
            'PENDING_EVALUATION',
            'ELIGIBLE',
            'COMPLETED',
            'NOT_COMPLETED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_tb_completions_attendance_rate CHECK (
        attendance_rate >= 0 AND attendance_rate <= 100
    ),
    CONSTRAINT ck_tb_completions_completed_minutes CHECK (completed_minutes >= 0),
    CONSTRAINT ck_tb_completions_credit_value CHECK (
        credit_value IS NULL OR credit_value BETWEEN 0.01 AND 999.99
    )
);
