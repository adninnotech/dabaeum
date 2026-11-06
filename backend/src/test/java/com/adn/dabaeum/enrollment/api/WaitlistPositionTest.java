package com.adn.dabaeum.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 대기 순번이 목록 응답으로 그대로 전달되는지 확인한다. 순번 계산(윈도우 함수) 자체의 검증은
 * {@code EnrollmentMapperIntegrationTest#waitlistedRowsGetSequentialPositionsInApplicationOrder}
 * 에서 실DB로 한다 — 저장하지 않고 조회 시 계산하므로 단위 테스트로는 계산 로직을 재현할 수 없다.
 */
class WaitlistPositionTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private final EnrollmentApiMapper mapper =
        new EnrollmentApiMapper(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 대기_순번이_응답에_그대로_전달된다() {
        EnrollmentListItem item = new EnrollmentListItem(waitlisted(), profile(), 2, 0, null);

        assertThat(mapper.toResponse(item).waitlistPosition()).isEqualTo(2);
    }

    @Test
    void 대기가_아니면_순번이_없다() {
        EnrollmentListItem item = new EnrollmentListItem(approved(), profile(), null, 0, null);

        assertThat(mapper.toResponse(item).waitlistPosition()).isNull();
    }

    private EnrolleeProfile profile() {
        return new EnrolleeProfile("이름", null, null, null, null, null);
    }

    private Enrollment waitlisted() {
        return new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.WAITLISTED, NOW, null, null,
            null, null, null, null, NOW, NOW);
    }

    private Enrollment approved() {
        return new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW, NOW, null,
            null, null, null, null, NOW, NOW);
    }
}
