package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceUtils;

class CourseEnrollmentConstraintIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Test
    void rejectsDuplicateActiveCourseCode() {
        UUID institutionId = insertInstitution();
        String courseCode = uniqueCode("course");

        insertCourse(institutionId, courseCode);

        assertSqlState("23505", () -> insertCourse(institutionId, courseCode));
    }

    @Test
    void allowsCodeReuseAfterSoftDelete() {
        UUID institutionId = insertInstitution();
        String courseCode = uniqueCode("course");
        UUID deletedCourseId = insertCourse(institutionId, courseCode);

        assertThat(jdbcTemplate.update(
            "UPDATE tb_courses SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
            deletedCourseId
        )).isEqualTo(1);

        InsertResult replacementCourse = insertCourseWithResult(institutionId, courseCode);
        assertThat(replacementCourse.updateCount()).isEqualTo(1);
        Integer courseCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_courses WHERE institution_id = ? AND course_code = ?",
            Integer.class,
            institutionId,
            courseCode
        );
        assertThat(courseCount).isEqualTo(2);
    }

    @Test
    void rejectsSecondRoleForSameInstructor() {
        UUID courseId = insertCourse(insertInstitution(), uniqueCode("course"));
        UUID userId = insertUser();

        insertInstructor(courseId, userId, "MAIN");

        assertSqlState("23505", () -> insertInstructor(courseId, userId, "ASSISTANT"));
    }

    @Test
    void rejectsSecondMainInstructor() {
        UUID courseId = insertCourse(insertInstitution(), uniqueCode("course"));

        insertInstructor(courseId, insertUser(), "MAIN");

        assertSqlState("23505", () -> insertInstructor(courseId, insertUser(), "MAIN"));
    }

    @Test
    void rejectsDuplicateSessionNumber() {
        UUID courseId = insertCourse(insertInstitution(), uniqueCode("course"));

        insertSession(courseId, 1, at("2026-08-01T09:00:00+09:00"), at("2026-08-01T10:00:00+09:00"));

        assertSqlState("23505", () -> insertSession(
            courseId,
            1,
            at("2026-08-08T09:00:00+09:00"),
            at("2026-08-08T10:00:00+09:00")
        ));
    }

    @Test
    void rejectsDuplicateActiveEnrollment() throws SQLException {
        assertDuplicateActiveEnrollment("APPLIED", "WAITLISTED");
        assertDuplicateActiveEnrollment("WAITLISTED", "APPROVED");
        assertDuplicateActiveEnrollment("APPROVED", "APPLIED");
    }

    @Test
    void allowsReapplicationAfterCancellation() {
        UUID courseId = insertCourse(insertInstitution(), uniqueCode("course"));
        UUID userId = insertUser();

        insertEnrollment(courseId, userId, "CANCELLED");

        InsertResult reappliedEnrollment = insertEnrollmentWithResult(courseId, userId, "APPLIED");
        assertThat(reappliedEnrollment.updateCount()).isEqualTo(1);
        Integer enrollmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_enrollments WHERE course_id = ? AND user_id = ?",
            Integer.class,
            courseId,
            userId
        );
        assertThat(enrollmentCount).isEqualTo(2);
    }

    @Test
    void rejectsInvalidSessionTime() {
        UUID courseId = insertCourse(insertInstitution(), uniqueCode("course"));
        OffsetDateTime startsAt = at("2026-08-01T10:00:00+09:00");

        assertSqlState("23514", () -> insertSession(courseId, 1, startsAt, startsAt));
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

    private UUID insertCourse(UUID institutionId, String courseCode) {
        return insertCourseWithResult(institutionId, courseCode).id();
    }

    private InsertResult insertCourseWithResult(UUID institutionId, String courseCode) {
        UUID courseId = UUID.randomUUID();
        int updateCount = jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            courseId,
            institutionId,
            courseCode,
            uniqueCode("course-title"),
            "OFFLINE",
            java.time.LocalDate.of(2026, 8, 1),
            java.time.LocalDate.of(2026, 8, 31),
            20
        );
        return new InsertResult(courseId, updateCount);
    }

    private UUID insertInstructor(UUID courseId, UUID userId, String instructorRole) {
        UUID instructorId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_instructors (id, course_id, user_id, instructor_role)
                VALUES (?, ?, ?, ?)
                """,
            instructorId,
            courseId,
            userId,
            instructorRole
        );
        return instructorId;
    }

    private UUID insertSession(
        UUID courseId,
        int sessionNo,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
    ) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_sessions (id, course_id, session_no, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?)
                """,
            sessionId,
            courseId,
            sessionNo,
            startsAt,
            endsAt
        );
        return sessionId;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId, String status) {
        return insertEnrollmentWithResult(courseId, userId, status).id();
    }

    private InsertResult insertEnrollmentWithResult(UUID courseId, UUID userId, String status) {
        UUID enrollmentId = UUID.randomUUID();
        int updateCount = jdbcTemplate.update(
            """
                INSERT INTO tb_enrollments (id, course_id, user_id, status)
                VALUES (?, ?, ?, ?)
                """,
            enrollmentId,
            courseId,
            userId,
            status
        );
        return new InsertResult(enrollmentId, updateCount);
    }

    private void assertDuplicateActiveEnrollment(String existingStatus, String duplicateStatus)
        throws SQLException {
        UUID courseId = insertCourse(insertInstitution(), uniqueCode("course"));
        UUID userId = insertUser();

        insertEnrollment(courseId, userId, existingStatus);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        Savepoint savepoint = connection.setSavepoint();
        assertSqlState("23505", () -> insertEnrollment(courseId, userId, duplicateStatus));
        connection.rollback(savepoint);
    }

    private OffsetDateTime at(String value) {
        return OffsetDateTime.parse(value);
    }

    private record InsertResult(UUID id, int updateCount) {
    }
}
