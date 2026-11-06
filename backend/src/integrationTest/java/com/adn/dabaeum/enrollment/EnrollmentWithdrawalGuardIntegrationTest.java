package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 수강 철회 가드가 읽는 출석·이수 판정 조회 (DefaultEnrollmentApplicationService.requireWithdrawable). */
class EnrollmentWithdrawalGuardIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    EnrollmentRepository repository;

    @Autowired
    CourseSessionRepository sessionRepository;

    @Test
    void 첫_회차_시작은_취소된_회차와_시각_없는_회차를_뺀_가장_이른_시각이다() {
        Fixture fixture = fixture();
        assertThat(sessionRepository.findFirstActiveStartByCourseId(fixture.courseId())).isEmpty();

        UUID cancelled = insertSession(fixture.courseId(), 1);
        jdbcTemplate.update("UPDATE tb_course_sessions SET status = 'CANCELLED' WHERE id = ?", cancelled);
        insertSession(fixture.courseId(), 3);
        insertSession(fixture.courseId(), 2);

        assertThat(sessionRepository.findFirstActiveStartByCourseId(fixture.courseId()))
            .contains(OffsetDateTime.parse("2026-08-02T09:00:00+09:00").toInstant());
    }

    @Test
    void 출석은_PRESENT_LATE_만_센다() {
        Fixture fixture = fixture();
        assertThat(repository.hasAttendance(fixture.enrollmentId())).isFalse();

        insertAttendance(fixture, insertSession(fixture.courseId(), 1), "ABSENT");
        insertAttendance(fixture, insertSession(fixture.courseId(), 2), "EXCUSED");
        assertThat(repository.hasAttendance(fixture.enrollmentId())).isFalse();

        insertAttendance(fixture, insertSession(fixture.courseId(), 3), "LATE");
        assertThat(repository.hasAttendance(fixture.enrollmentId())).isTrue();
    }

    @Test
    void 이수_판정은_ELIGIBLE_COMPLETED_만_센다() {
        Fixture fixture = fixture();
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_completions (id, enrollment_id, attendance_rate, completed_minutes)
                VALUES (?, ?, ?, ?)
                """,
            completionId, fixture.enrollmentId(), 100.0, 60);
        assertThat(repository.hasCompletionDecision(fixture.enrollmentId())).isFalse();

        jdbcTemplate.update("UPDATE tb_completions SET status = 'NOT_COMPLETED' WHERE id = ?", completionId);
        assertThat(repository.hasCompletionDecision(fixture.enrollmentId())).isFalse();

        jdbcTemplate.update("UPDATE tb_completions SET status = 'ELIGIBLE' WHERE id = ?", completionId);
        assertThat(repository.hasCompletionDecision(fixture.enrollmentId())).isTrue();
    }

    private Fixture fixture() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type,
                    start_date, end_date, capacity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            courseId, institutionId, uniqueCode("course"), uniqueCode("course-title"), "OFFLINE",
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, 'APPROVED')",
            enrollmentId, courseId, userId);
        return new Fixture(courseId, enrollmentId);
    }

    private UUID insertSession(UUID courseId, int sessionNo) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_sessions (id, course_id, session_no, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?)
                """,
            sessionId, courseId, sessionNo,
            OffsetDateTime.parse("2026-08-0" + sessionNo + "T09:00:00+09:00"),
            OffsetDateTime.parse("2026-08-0" + sessionNo + "T10:00:00+09:00"));
        return sessionId;
    }

    private void insertAttendance(Fixture fixture, UUID sessionId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO tb_attendance_records (
                    id, course_id, session_id, enrollment_id, attendance_method, status, source
                ) VALUES (?, ?, ?, ?, 'ADMIN', ?, 'ADMIN_WEB')
                """,
            UUID.randomUUID(), fixture.courseId(), sessionId, fixture.enrollmentId(), status);
    }

    private record Fixture(UUID courseId, UUID enrollmentId) {
    }
}
