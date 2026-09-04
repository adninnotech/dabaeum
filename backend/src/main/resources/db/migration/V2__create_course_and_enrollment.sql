CREATE TABLE tb_courses (
    id UUID NOT NULL,
    institution_id UUID NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    education_type VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    recruit_start_date DATE,
    recruit_end_date DATE,
    capacity INTEGER NOT NULL,
    location VARCHAR(500),
    online_url VARCHAR(1000),
    credit_bank_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    credit_value NUMERIC(5, 2),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_tb_courses PRIMARY KEY (id),
    CONSTRAINT fk_tb_courses_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_courses_dates CHECK (start_date <= end_date),
    CONSTRAINT ck_tb_courses_recruit_dates CHECK (
        recruit_start_date IS NULL
        OR recruit_end_date IS NULL
        OR recruit_start_date <= recruit_end_date
    ),
    CONSTRAINT ck_tb_courses_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_tb_courses_credit_value CHECK (
        credit_value IS NULL OR credit_value BETWEEN 0.01 AND 999.99
    ),
    CONSTRAINT ck_tb_courses_education_type CHECK (
        education_type IN ('OFFLINE', 'ONLINE', 'HYBRID')
    ),
    CONSTRAINT ck_tb_courses_status CHECK (
        status IN (
            'DRAFT',
            'RECRUITING',
            'RECRUITMENT_CLOSED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED'
        )
    )
);

CREATE UNIQUE INDEX uq_tb_courses_code_active
    ON tb_courses (institution_id, course_code)
    WHERE deleted_at IS NULL;

CREATE TABLE tb_course_instructors (
    id UUID NOT NULL,
    course_id UUID NOT NULL,
    user_id UUID NOT NULL,
    instructor_role VARCHAR(30) NOT NULL DEFAULT 'MAIN',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_course_instructors PRIMARY KEY (id),
    CONSTRAINT fk_tb_course_instructors_course_id
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_course_instructors_user_id
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_course_instructors_user UNIQUE (course_id, user_id),
    CONSTRAINT ck_tb_course_instructors_role CHECK (
        instructor_role IN ('MAIN', 'ASSISTANT')
    )
);

CREATE UNIQUE INDEX uq_tb_course_instructors_main
    ON tb_course_instructors (course_id)
    WHERE instructor_role = 'MAIN';

CREATE TABLE tb_course_sessions (
    id UUID NOT NULL,
    course_id UUID NOT NULL,
    session_no INTEGER NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(500),
    attendance_opens_at TIMESTAMPTZ,
    attendance_closes_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_course_sessions PRIMARY KEY (id),
    CONSTRAINT fk_tb_course_sessions_course_id
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_course_sessions_no UNIQUE (course_id, session_no),
    CONSTRAINT uq_tb_course_sessions_id_course UNIQUE (id, course_id),
    CONSTRAINT ck_tb_course_sessions_no CHECK (session_no >= 1),
    CONSTRAINT ck_tb_course_sessions_time CHECK (starts_at < ends_at),
    CONSTRAINT ck_tb_course_sessions_attendance_time CHECK (
        attendance_opens_at IS NULL
        OR attendance_closes_at IS NULL
        OR attendance_opens_at < attendance_closes_at
    ),
    CONSTRAINT ck_tb_course_sessions_status CHECK (
        status IN ('SCHEDULED', 'OPEN', 'COMPLETED', 'CANCELLED')
    )
);

CREATE TABLE tb_enrollments (
    id UUID NOT NULL,
    course_id UUID NOT NULL,
    user_id UUID NOT NULL,
    applied_by UUID,
    application_type VARCHAR(30) NOT NULL DEFAULT 'SELF',
    status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    cancellation_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_enrollments PRIMARY KEY (id),
    CONSTRAINT fk_tb_enrollments_course_id
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_enrollments_user_id
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_enrollments_applied_by
        FOREIGN KEY (applied_by) REFERENCES tb_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_enrollments_id_course UNIQUE (id, course_id),
    CONSTRAINT ck_tb_enrollments_application_type CHECK (
        application_type IN ('SELF', 'ADMIN_PROXY', 'EXTERNAL_SYNC')
    ),
    CONSTRAINT ck_tb_enrollments_status CHECK (
        status IN (
            'APPLIED',
            'WAITLISTED',
            'APPROVED',
            'REJECTED',
            'CANCELLED',
            'WITHDRAWN'
        )
    )
);

CREATE UNIQUE INDEX uq_tb_enrollments_active
    ON tb_enrollments (course_id, user_id)
    WHERE status IN ('APPLIED', 'WAITLISTED', 'APPROVED');
