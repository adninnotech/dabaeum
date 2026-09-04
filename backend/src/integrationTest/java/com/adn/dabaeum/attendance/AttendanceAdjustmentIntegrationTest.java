package com.adn.dabaeum.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.attendance.application.AdjustAttendanceCommand;
import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustment;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentRepository;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceAdjustmentIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired AttendanceApplicationService attendanceService;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired AttendanceAdjustmentRepository adjustmentRepository;

    @Test
    void appendsAdjustmentHistoryAndPreservesEarlierRows() {
        Fixture fixture = fixture();
        Instant now = Instant.now();
        Attendance original = new Attendance(
            UUID.randomUUID(), fixture.courseId(), fixture.sessionId(), fixture.enrollmentId(), null,
            AttendanceMethod.ADMIN, AttendanceStatus.PRESENT, now, AttendanceSource.ADMIN_WEB,
            fixture.managerId(), now, now);
        attendanceRepository.save(original);

        AuthenticatedUserContext manager = new AuthenticatedUserContext(
            fixture.managerId(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", fixture.institutionId())));
        attendanceService.adjust(new AdjustAttendanceCommand(
            original.id(), AttendanceStatus.LATE, "현장 확인 1"), manager);
        attendanceService.adjust(new AdjustAttendanceCommand(
            original.id(), AttendanceStatus.EXCUSED, "현장 확인 2"), manager);

        List<AttendanceAdjustment> history = adjustmentRepository.findByAttendanceId(original.id());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).beforeStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(history.get(0).afterStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(history.get(0).reason()).isEqualTo("현장 확인 1");
        assertThat(history.get(1).beforeStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(history.get(1).afterStatus()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(attendanceRepository.findById(original.id()))
            .get().extracting(Attendance::status).isEqualTo(AttendanceStatus.EXCUSED);
    }

    private Fixture fixture() {
        UUID institutionId = insertInstitution();
        UUID managerId = insertUser();
        UUID learnerId = insertUser();
        UUID courseId = insertCourse(institutionId);
        insertInstructor(courseId, managerId);
        UUID sessionId = insertSession(courseId);
        UUID enrollmentId = insertEnrollment(courseId, learnerId);
        return new Fixture(institutionId, managerId, courseId, sessionId, enrollmentId);
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, uniqueCode("ai"), uniqueCode("an"));
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("au");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            id, code, code + "@example.test");
        return id;
    }

    private UUID insertCourse(UUID institutionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, institutionId, uniqueCode("ac"), uniqueCode("at"), "OFFLINE",
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        return id;
    }

    private void insertInstructor(UUID courseId, UUID userId) {
        jdbcTemplate.update("""
            INSERT INTO tb_course_instructors (id, course_id, user_id, instructor_role)
            VALUES (?, ?, ?, 'MAIN')
            """, UUID.randomUUID(), courseId, userId);
    }

    private UUID insertSession(UUID courseId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_course_sessions (
                id, course_id, session_no, starts_at, ends_at, status
            ) VALUES (?, ?, ?, ?, ?, 'COMPLETED')
            """, id, courseId, 1, OffsetDateTime.now().minusHours(2),
            OffsetDateTime.now().minusHours(1));
        return id;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, id, courseId, userId, OffsetDateTime.now().minusHours(3));
        return id;
    }

    private record Fixture(
        UUID institutionId,
        UUID managerId,
        UUID courseId,
        UUID sessionId,
        UUID enrollmentId
    ) {
    }
}
