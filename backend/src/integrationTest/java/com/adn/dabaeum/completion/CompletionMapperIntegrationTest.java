package com.adn.dabaeum.completion;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CompletionMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CompletionRepository repository;

    @Test
    void roundTripsNullableFieldsAndSupportsLockAndExpectedStatusUpdates() {
        Fixture fixture = insertEnrollmentFixture();
        UUID enrollmentId = fixture.enrollmentId();
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        UUID completionId = UUID.randomUUID();
        Completion pending = completion(completionId, enrollmentId, CompletionStatus.PENDING_EVALUATION,
            BigDecimal.ZERO.setScale(2), 0, null, null, null, null, null, null, now, now);

        repository.save(pending);
        assertThat(repository.findByEnrollmentId(enrollmentId)).contains(pending);
        assertThat(repository.findByEnrollmentIdForUpdate(enrollmentId)).contains(pending);

        Completion eligible = completion(completionId, enrollmentId, CompletionStatus.ELIGIBLE,
            new BigDecimal("88.50"), 120, new BigDecimal("1.00"), now.plusSeconds(1),
            null, null, null, null, now, now.plusSeconds(1));
        assertThat(repository.updateEvaluation(eligible, CompletionStatus.PENDING_EVALUATION)).isTrue();
        assertThat(repository.updateEvaluation(eligible, CompletionStatus.PENDING_EVALUATION)).isFalse();
        assertThat(repository.findByEnrollmentId(enrollmentId)).contains(eligible);

        Completion completed = completion(completionId, enrollmentId, CompletionStatus.COMPLETED,
            eligible.attendanceRate(), eligible.completedMinutes(), eligible.creditValue(),
            eligible.evaluatedAt(), now.plusSeconds(2), fixture.userId(), now.plusSeconds(2),
            null, eligible.createdAt(), now.plusSeconds(2));
        assertThat(repository.confirm(completed, CompletionStatus.ELIGIBLE)).isTrue();
        assertThat(repository.confirm(completed, CompletionStatus.ELIGIBLE)).isFalse();
        assertThat(repository.findByEnrollmentId(enrollmentId)).contains(completed);
    }

    @Test
    void rejectsSecondCompletionForSameEnrollment() {
        UUID enrollmentId = insertEnrollmentFixture().enrollmentId();
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        repository.save(completion(UUID.randomUUID(), enrollmentId,
            CompletionStatus.PENDING_EVALUATION, BigDecimal.ZERO.setScale(2), 0, null,
            null, null, null, null, null, now, now));

        assertSqlState("23505", () -> repository.save(completion(
            UUID.randomUUID(), enrollmentId, CompletionStatus.PENDING_EVALUATION,
            BigDecimal.ZERO.setScale(2), 0, null, null, null, null, null, null, now, now)));
    }

    private Completion completion(
        UUID id,
        UUID enrollmentId,
        CompletionStatus status,
        BigDecimal attendanceRate,
        int completedMinutes,
        BigDecimal creditValue,
        Instant evaluatedAt,
        Instant completedAt,
        UUID confirmedBy,
        Instant confirmedAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Completion(id, enrollmentId, status, attendanceRate, completedMinutes,
            creditValue, evaluatedAt, completedAt, confirmedBy, confirmedAt, failureReason,
            createdAt, updatedAt);
    }

    private Fixture insertEnrollmentFixture() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("ci"), uniqueCode("cn"));
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("cu");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20)
            """, courseId, institutionId, uniqueCode("cc"), uniqueCode("ct"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, userId, OffsetDateTime.now());
        return new Fixture(enrollmentId, userId);
    }

    private record Fixture(UUID enrollmentId, UUID userId) {
    }
}
