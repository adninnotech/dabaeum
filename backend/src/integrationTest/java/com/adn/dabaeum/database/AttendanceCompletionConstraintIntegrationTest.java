package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceUtils;

class AttendanceCompletionConstraintIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Test
    void acceptsAttendanceForSameCourse() {
        AttendanceFixture fixture = insertAttendanceFixture();
        UUID attendanceId = UUID.randomUUID();

        assertThat(insertAttendance(attendanceId, fixture)).isEqualTo(1);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_attendance_records WHERE id = ?",
            Integer.class,
            attendanceId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsAttendanceAcrossCourses() {
        UUID institutionId = insertInstitution();
        UUID userId = insertUser();
        UUID courseAId = insertCourse(institutionId);
        UUID courseBId = insertCourse(institutionId);
        UUID sessionId = insertSession(courseAId, 1);
        UUID enrollmentId = insertEnrollment(courseBId, userId);

        assertSqlState("23503", () -> jdbcTemplate.update(
            """
                INSERT INTO tb_attendance_records (
                    id, course_id, session_id, enrollment_id, attendance_method, status, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            courseAId,
            sessionId,
            enrollmentId,
            "ADMIN",
            "PRESENT",
            "ADMIN_WEB"
        ));
    }

    @Test
    void rejectsDuplicateAttendance() {
        AttendanceFixture fixture = insertAttendanceFixture();

        insertAttendance(UUID.randomUUID(), fixture);

        assertSqlState("23505", () -> insertAttendance(UUID.randomUUID(), fixture));
    }

    @Test
    void rejectsOrphanAdjustment() {
        UUID adjustedBy = insertUser();

        assertSqlState("23503", () -> jdbcTemplate.update(
            """
                INSERT INTO tb_attendance_adjustments (
                    id, attendance_record_id, before_status, after_status, reason, adjusted_by
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ABSENT",
            "PRESENT",
            uniqueCode("reason"),
            adjustedBy
        ));
    }

    @Test
    void rejectsSecondCompletionForEnrollment() {
        UUID enrollmentId = insertEnrollmentFixture();

        insertCompletion(UUID.randomUUID(), enrollmentId, null, 0, 0);

        assertSqlState("23505", () ->
            insertCompletion(UUID.randomUUID(), enrollmentId, null, 0, 0)
        );
    }

    @Test
    void rejectsAttendanceRateOutsideRange() throws SQLException {
        UUID firstEnrollmentId = insertEnrollmentFixture();
        UUID secondEnrollmentId = insertEnrollmentFixture();

        assertSqlStateAndRollback("23514", () ->
            insertCompletion(UUID.randomUUID(), firstEnrollmentId, null, -0.01, 0)
        );
        assertSqlStateAndRollback("23514", () ->
            insertCompletion(UUID.randomUUID(), secondEnrollmentId, null, 100.01, 0)
        );
    }

    @Test
    void rejectsNegativeCompletedMinutes() {
        UUID enrollmentId = insertEnrollmentFixture();

        assertSqlState("23514", () ->
            insertCompletion(UUID.randomUUID(), enrollmentId, null, 0, -1)
        );
    }

    @Test
    void rejectsCompletionCreditValueOutsideRange() throws SQLException {
        UUID zeroEnrollmentId = insertEnrollmentFixture();
        UUID excessiveEnrollmentId = insertEnrollmentFixture();

        assertSqlStateAndRollback("23514", () ->
            insertCompletion(UUID.randomUUID(), zeroEnrollmentId, 0.00, 0, 0)
        );
        assertSqlStateAndRollback("22003", () ->
            insertCompletion(UUID.randomUUID(), excessiveEnrollmentId, 1000.00, 0, 0)
        );
    }

    @Test
    void acceptsCompletionCreditValueBoundaries() {
        UUID nullEnrollmentId = insertEnrollmentFixture();
        UUID lowerEnrollmentId = insertEnrollmentFixture();
        UUID upperEnrollmentId = insertEnrollmentFixture();

        assertThat(insertCompletion(UUID.randomUUID(), nullEnrollmentId, null, 0, 0)).isEqualTo(1);
        assertThat(insertCompletion(UUID.randomUUID(), lowerEnrollmentId, 0.01, 0, 0)).isEqualTo(1);
        assertThat(insertCompletion(UUID.randomUUID(), upperEnrollmentId, 999.99, 0, 0)).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                  FROM tb_completions
                 WHERE enrollment_id IN (?, ?, ?)
                """,
            Integer.class,
            nullEnrollmentId,
            lowerEnrollmentId,
            upperEnrollmentId
        );
        assertThat(count).isEqualTo(3);
    }

    private AttendanceFixture insertAttendanceFixture() {
        UUID institutionId = insertInstitution();
        UUID userId = insertUser();
        UUID courseId = insertCourse(institutionId);
        UUID sessionId = insertSession(courseId, 1);
        UUID enrollmentId = insertEnrollment(courseId, userId);
        return new AttendanceFixture(courseId, sessionId, enrollmentId);
    }

    private UUID insertEnrollmentFixture() {
        UUID institutionId = insertInstitution();
        UUID userId = insertUser();
        UUID courseId = insertCourse(institutionId);
        return insertEnrollment(courseId, userId);
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId,
            uniqueCode("institution"),
            uniqueCode("institution-name")
        );
        return institutionId;
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId,
            userCode,
            userCode + "@example.test"
        );
        return userId;
    }

    private UUID insertCourse(UUID institutionId) {
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type,
                    start_date, end_date, capacity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            courseId,
            institutionId,
            uniqueCode("course"),
            uniqueCode("course-title"),
            "OFFLINE",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            20
        );
        return courseId;
    }

    private UUID insertSession(UUID courseId, int sessionNo) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_sessions (id, course_id, session_no, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?)
                """,
            sessionId,
            courseId,
            sessionNo,
            OffsetDateTime.parse("2026-08-01T09:00:00+09:00"),
            OffsetDateTime.parse("2026-08-01T10:00:00+09:00")
        );
        return sessionId;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_enrollments (id, course_id, user_id, status)
                VALUES (?, ?, ?, ?)
                """,
            enrollmentId,
            courseId,
            userId,
            "APPLIED"
        );
        return enrollmentId;
    }

    private int insertAttendance(UUID attendanceId, AttendanceFixture fixture) {
        return jdbcTemplate.update(
            """
                INSERT INTO tb_attendance_records (
                    id, course_id, session_id, enrollment_id, attendance_method, status, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            attendanceId,
            fixture.courseId(),
            fixture.sessionId(),
            fixture.enrollmentId(),
            "ADMIN",
            "PRESENT",
            "ADMIN_WEB"
        );
    }

    private int insertCompletion(
        UUID completionId,
        UUID enrollmentId,
        Double creditValue,
        double attendanceRate,
        int completedMinutes
    ) {
        return jdbcTemplate.update(
            """
                INSERT INTO tb_completions (
                    id, enrollment_id, credit_value, attendance_rate, completed_minutes
                ) VALUES (?, ?, ?, ?, ?)
                """,
            completionId,
            enrollmentId,
            creditValue,
            attendanceRate,
            completedMinutes
        );
    }

    private void assertSqlStateAndRollback(
        String expectedSqlState,
        ThrowingCallable operation
    ) throws SQLException {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        Savepoint savepoint = connection.setSavepoint();
        assertSqlState(expectedSqlState, operation);
        connection.rollback(savepoint);
    }

    private record AttendanceFixture(UUID courseId, UUID sessionId, UUID enrollmentId) {
    }
}
