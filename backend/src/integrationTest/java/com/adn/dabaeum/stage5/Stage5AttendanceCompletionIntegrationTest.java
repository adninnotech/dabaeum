package com.adn.dabaeum.stage5;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.RecordAttendanceCommand;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.application.CompletionApplicationService;
import com.adn.dabaeum.completion.application.EvaluateCompletionCommand;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class Stage5AttendanceCompletionIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired AttendanceQrTokenApplicationService qrService;
    @Autowired AttendanceApplicationService attendanceService;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired CompletionApplicationService completionService;

    @Test
    void runsQrAttendanceAdjustmentSummaryAndCompletionEndToEnd() {
        Fixture fixture = fixture();
        AuthenticatedUserContext manager = manager(fixture.managerId(), fixture.institutionId());
        String displayedToken = qrService.issue(fixture.sessionId(), manager).token();

        Attendance first = attendanceService.record(new RecordAttendanceCommand(
            fixture.sessionId(), fixture.firstEnrollmentId(), AttendanceMethod.QR,
            AttendanceStatus.PRESENT, null, AttendanceSource.APP, displayedToken),
            learner(fixture.firstLearnerId(), fixture.institutionId()));
        attendanceService.record(new RecordAttendanceCommand(
            fixture.sessionId(), fixture.secondEnrollmentId(), AttendanceMethod.QR,
            AttendanceStatus.PRESENT, null, AttendanceSource.APP, displayedToken),
            learner(fixture.secondLearnerId(), fixture.institutionId()));

        attendanceService.adjust(
            new com.adn.dabaeum.attendance.application.AdjustAttendanceCommand(
                first.id(), AttendanceStatus.LATE, "현장 확인"), manager);
        jdbcTemplate.update("UPDATE tb_course_sessions SET status = 'COMPLETED' WHERE id = ?",
            fixture.sessionId());

        AttendanceMetrics summary = attendanceService.summary(
            fixture.firstEnrollmentId(), learner(fixture.firstLearnerId(), fixture.institutionId()));
        assertThat(summary.totalSessions()).isEqualTo(1);
        assertThat(summary.lateCount()).isEqualTo(1);
        assertThat(summary.attendanceRate()).isEqualByComparingTo("100.00");

        Completion evaluated = completionService.evaluate(new EvaluateCompletionCommand(
            fixture.firstEnrollmentId(), summary.attendanceRate(), summary.completedMinutes(),
            null, null), manager);
        assertThat(evaluated.status()).isEqualTo(CompletionStatus.ELIGIBLE);
        Completion completed = completionService.confirm(fixture.firstEnrollmentId(), manager);
        assertThat(completed.status()).isEqualTo(CompletionStatus.COMPLETED);

        Integer outboxCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM tb_outbox_events
             WHERE aggregate_id = ? AND event_type = 'COMPLETION_CONFIRMED'
               AND status = 'PENDING'
            """, Integer.class, completed.id());
        assertThat(outboxCount).isEqualTo(1);
        assertThat(attendanceRepository.findByEnrollment(fixture.secondEnrollmentId())).hasSize(1);
    }

    private Fixture fixture() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("ei"), uniqueCode("en"));
        UUID managerId = insertUser("em");
        UUID firstLearnerId = insertUser("e1");
        UUID secondLearnerId = insertUser("e2");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity, status
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20, 'IN_PROGRESS')
            """, courseId, institutionId, uniqueCode("ec"), uniqueCode("et"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        jdbcTemplate.update("""
            INSERT INTO tb_course_instructors (id, course_id, user_id, instructor_role)
            VALUES (?, ?, ?, 'MAIN')
            """, UUID.randomUUID(), courseId, managerId);
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO tb_course_sessions (
                id, course_id, session_no, starts_at, ends_at,
                attendance_opens_at, attendance_closes_at, status
            ) VALUES (?, ?, 1, ?, ?, ?, ?, 'OPEN')
            """, sessionId, courseId, now.minusMinutes(10), now.plusMinutes(2),
            now.minusMinutes(1), now.plusMinutes(1));
        UUID firstEnrollmentId = insertEnrollment(courseId, firstLearnerId);
        UUID secondEnrollmentId = insertEnrollment(courseId, secondLearnerId);
        return new Fixture(institutionId, managerId, firstLearnerId, secondLearnerId,
            courseId, sessionId, firstEnrollmentId, secondEnrollmentId);
    }

    private UUID insertUser(String prefix) {
        UUID id = UUID.randomUUID();
        String code = uniqueCode(prefix);
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            id, code, code + "@example.test");
        return id;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, id, courseId, userId, OffsetDateTime.now().minusMinutes(5));
        return id;
    }

    private AuthenticatedUserContext manager(UUID userId, UUID institutionId) {
        return new AuthenticatedUserContext(userId, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", institutionId)));
    }

    private AuthenticatedUserContext learner(UUID userId, UUID institutionId) {
        return new AuthenticatedUserContext(userId, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", institutionId)));
    }

    private record Fixture(
        UUID institutionId,
        UUID managerId,
        UUID firstLearnerId,
        UUID secondLearnerId,
        UUID courseId,
        UUID sessionId,
        UUID firstEnrollmentId,
        UUID secondEnrollmentId
    ) {
    }
}
