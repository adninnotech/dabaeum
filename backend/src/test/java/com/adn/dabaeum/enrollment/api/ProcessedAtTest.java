package com.adn.dabaeum.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** C-3 처리 일시 단일화. 기존 세 필드(approvedAt·rejectedAt·cancelledAt)는 하위호환으로 남긴다. */
class ProcessedAtTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    private final EnrollmentApiMapper mapper =
        new EnrollmentApiMapper(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 승인건은_approvedAt_이_처리일시다() {
        Instant approvedAt = NOW.plusSeconds(60);
        Enrollment enrollment = new Enrollment(ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW, approvedAt,
            null, null, null, null, null, NOW, approvedAt);

        EnrollmentResponse response = mapper.toResponse(enrollment);

        assertThat(response.processedAt()).isEqualTo(approvedAt);
        assertThat(response.approvedAt()).isEqualTo(approvedAt);
    }

    @Test
    void 거절건은_rejectedAt_이_처리일시다() {
        Instant rejectedAt = NOW.plusSeconds(60);
        Enrollment enrollment = new Enrollment(ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.REJECTED, NOW, null,
            rejectedAt, null, null, "사유", null, NOW, rejectedAt);

        assertThat(mapper.toResponse(enrollment).processedAt()).isEqualTo(rejectedAt);
    }

    @Test
    void 취소건은_cancelledAt_이_처리일시다() {
        Instant cancelledAt = NOW.plusSeconds(60);
        Enrollment enrollment = new Enrollment(ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.CANCELLED, NOW, null,
            null, cancelledAt, null, null, null, NOW, cancelledAt);

        assertThat(mapper.toResponse(enrollment).processedAt()).isEqualTo(cancelledAt);
    }

    @Test
    void 승인후_철회건은_withdrawnAt_이_처리일시다() {
        Instant approvedAt = NOW.plusSeconds(60);
        Instant withdrawnAt = NOW.plusSeconds(120);
        Enrollment enrollment = new Enrollment(ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.WITHDRAWN, NOW, approvedAt,
            null, null, withdrawnAt, null, null, NOW, withdrawnAt);

        EnrollmentResponse response = mapper.toResponse(enrollment);

        assertThat(response.processedAt()).isEqualTo(withdrawnAt);
        assertThat(response.approvedAt()).isEqualTo(approvedAt);
    }

    @Test
    void 대기중이면_처리일시가_없다() {
        Enrollment enrollment = new Enrollment(ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPLIED, NOW, null,
            null, null, null, null, null, NOW, NOW);

        assertThat(mapper.toResponse(enrollment).processedAt()).isNull();
    }
}
