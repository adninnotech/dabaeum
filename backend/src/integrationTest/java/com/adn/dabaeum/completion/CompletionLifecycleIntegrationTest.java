package com.adn.dabaeum.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.application.CompletionApplicationService;
import com.adn.dabaeum.completion.application.EvaluateCompletionCommand;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CompletionLifecycleIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CompletionApplicationService completionService;
    @Autowired CompletionRepository completionRepository;

    @Test
    void evaluatesReevaluatesConfirmsAndWritesOnePendingOutboxEvent() {
        Fixture fixture = fixture();
        AuthenticatedUserContext manager = manager(fixture.managerId(), fixture.institutionId());
        EvaluateCompletionCommand eligible = new EvaluateCompletionCommand(
            fixture.enrollmentId(), new BigDecimal("100.00"), 60, null, null);

        Completion first = completionService.evaluate(eligible, manager);
        assertThat(first.status()).isEqualTo(CompletionStatus.ELIGIBLE);

        Completion failed = completionService.evaluate(new EvaluateCompletionCommand(
            fixture.enrollmentId(), new BigDecimal("100.00"), 60, null, "기준 재평가"), manager);
        assertThat(failed.status()).isEqualTo(CompletionStatus.NOT_COMPLETED);

        Completion eligibleAgain = completionService.evaluate(eligible, manager);
        assertThat(eligibleAgain.status()).isEqualTo(CompletionStatus.ELIGIBLE);

        Completion completed = completionService.confirm(fixture.enrollmentId(), manager);
        assertThat(completed.status()).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(completed.confirmedBy()).isEqualTo(fixture.managerId());

        assertThatThrownBy(() -> completionService.confirm(fixture.enrollmentId(), manager))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.COMPLETION_STATUS_CONFLICT);

        Integer outboxCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM tb_outbox_events
             WHERE aggregate_type = 'COMPLETION'
               AND aggregate_id = ?
               AND event_type = 'COMPLETION_CONFIRMED'
               AND status = 'PENDING'
            """, Integer.class, completed.id());
        assertThat(outboxCount).isEqualTo(1);
        String payload = jdbcTemplate.queryForObject("""
            SELECT payload::text FROM tb_outbox_events
             WHERE aggregate_id = ? AND event_type = 'COMPLETION_CONFIRMED'
            """, String.class, completed.id());
        assertThat(payload).contains("completionId", "courseId", "enrollmentId")
            .doesNotContain("email", "phone", "did", "credential", "token");
        assertThat(completionRepository.findByEnrollmentId(fixture.enrollmentId()))
            .get().extracting(Completion::status).isEqualTo(CompletionStatus.COMPLETED);
    }

    private Fixture fixture() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("li"), uniqueCode("ln"));
        UUID managerId = insertUser("lm");
        UUID learnerId = insertUser("ll");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity, status
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20, 'IN_PROGRESS')
            """, courseId, institutionId, uniqueCode("lc"), uniqueCode("lt"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        jdbcTemplate.update("""
            INSERT INTO tb_course_instructors (id, course_id, user_id, instructor_role)
            VALUES (?, ?, ?, 'MAIN')
            """, UUID.randomUUID(), courseId, managerId);
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime starts = OffsetDateTime.parse("2026-08-01T09:00:00+09:00");
        OffsetDateTime ends = OffsetDateTime.parse("2026-08-01T10:00:00+09:00");
        jdbcTemplate.update("""
            INSERT INTO tb_course_sessions (
                id, course_id, session_no, starts_at, ends_at, status
            ) VALUES (?, ?, 1, ?, ?, 'COMPLETED')
            """, sessionId, courseId, starts, ends);
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, learnerId, starts.minusHours(1));
        jdbcTemplate.update("""
            INSERT INTO tb_attendance_records (
                id, course_id, session_id, enrollment_id, attendance_method, status,
                checked_at, source, created_by
            ) VALUES (?, ?, ?, ?, 'ADMIN', 'PRESENT', ?, 'ADMIN_WEB', ?)
            """, UUID.randomUUID(), courseId, sessionId, enrollmentId, ends, managerId);
        return new Fixture(institutionId, managerId, courseId, enrollmentId);
    }

    private UUID insertUser(String prefix) {
        UUID userId = UUID.randomUUID();
        String code = uniqueCode(prefix);
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, code, code + "@example.test");
        return userId;
    }

    private AuthenticatedUserContext manager(UUID userId, UUID institutionId) {
        return new AuthenticatedUserContext(userId, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", institutionId)));
    }

    private record Fixture(UUID institutionId, UUID managerId, UUID courseId, UUID enrollmentId) {
    }
}
