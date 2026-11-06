-- 강좌 목록·상세의 승인 신청자 수 집계(COUNT WHERE course_id = ? AND status = 'APPROVED')를 받쳐준다.
CREATE INDEX IF NOT EXISTS ix_tb_enrollments_course_status
    ON tb_enrollments (course_id, status);
