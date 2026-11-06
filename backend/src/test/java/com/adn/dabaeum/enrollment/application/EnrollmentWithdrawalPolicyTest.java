package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.config.EnrollmentWithdrawalProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentWithdrawalPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private final CourseSessionRepository sessions = mock(CourseSessionRepository.class);
    private final EnrollmentWithdrawalPolicy policy =
        new EnrollmentWithdrawalPolicy(sessions, new EnrollmentWithdrawalProperties(Duration.ofHours(24)));

    @Test
    void 첫_회차가_있으면_그_시작_24시간_전이_마감이다() {
        Course course = course(LocalDate.of(2026, 9, 1));
        Instant first = Instant.parse("2026-09-01T05:00:00Z"); // 서울 14:00
        when(sessions.findFirstActiveStartByCourseId(course.id())).thenReturn(Optional.of(first));

        assertThat(policy.selfWithdrawDeadline(course))
            .contains(Instant.parse("2026-08-31T05:00:00Z"));
    }

    @Test
    void 회차가_없으면_startDate_서울_자정_24시간_전이_마감이다() {
        Course course = course(LocalDate.of(2026, 9, 1));
        when(sessions.findFirstActiveStartByCourseId(course.id())).thenReturn(Optional.empty());

        // 2026-09-01 00:00 KST = 2026-08-31T15:00Z, 하루 전은 2026-08-30T15:00Z
        assertThat(policy.selfWithdrawDeadline(course))
            .contains(Instant.parse("2026-08-30T15:00:00Z"));
    }

    @Test
    void 회차도_startDate_도_없으면_마감이_없다() {
        Course course = course(null);
        when(sessions.findFirstActiveStartByCourseId(course.id())).thenReturn(Optional.empty());

        assertThat(policy.selfWithdrawDeadline(course)).isEmpty();
    }

    @Test
    void 마감_0시간이면_수업_시작_시각이_마감이다() {
        EnrollmentWithdrawalPolicy zero =
            new EnrollmentWithdrawalPolicy(sessions, new EnrollmentWithdrawalProperties(Duration.ZERO));
        Course course = course(LocalDate.of(2026, 9, 1));
        when(sessions.findFirstActiveStartByCourseId(course.id())).thenReturn(Optional.of(NOW));

        assertThat(zero.selfWithdrawDeadline(course)).contains(NOW);
    }

    private static Course course(LocalDate startDate) {
        CourseStatus status = startDate == null ? CourseStatus.DRAFT : CourseStatus.RECRUITING;
        return new Course(UUID.randomUUID(), UUID.randomUUID(), "C-1", "과정", null, null,
            CourseEducationType.OFFLINE, startDate, startDate == null ? null : startDate.plusMonths(1),
            null, null, startDate == null ? null : 20, null, null, false, (BigDecimal) null,
            status, NOW, NOW, null);
    }
}
