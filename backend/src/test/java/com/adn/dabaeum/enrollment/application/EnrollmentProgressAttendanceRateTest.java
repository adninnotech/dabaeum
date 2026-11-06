package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.AttendanceMetricsQuery;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.EnrollmentProgressView;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 강사 수강 현황의 출석률은 학습자 출결 요약과 같은 계산(AttendanceMetricsQuery)에서 온다(QA DATA-04). */
class EnrollmentProgressAttendanceRateTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");
    private static final UUID INSTRUCTOR_ID = UUID.randomUUID();
    private static final UUID ENROLLMENT_ID = UUID.randomUUID();

    private final EnrollmentQueryRepository queryRepository = mock(EnrollmentQueryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AttendanceMetricsQuery metricsQuery = mock(AttendanceMetricsQuery.class);
    private final DefaultEnrollmentQueryService service = new DefaultEnrollmentQueryService(
        queryRepository, new AuthorizationPolicy(), userRepository, metricsQuery,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void fillsTheSameAttendanceRateAsTheLearnerSummary() {
        when(userRepository.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(mock(User.class)));
        when(queryRepository.findInstructorEnrollmentProgress(any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(view()));
        when(queryRepository.countInstructorEnrollmentProgress(any(), any())).thenReturn(1L);
        when(metricsQuery.calculate(ENROLLMENT_ID, NOW)).thenReturn(new AttendanceMetrics(
            ENROLLMENT_ID, 3, 1, 1, 0, 1, new BigDecimal("100.00"), 240));

        EnrollmentProgressPage page = service.listInstructorEnrollmentProgressOf(INSTRUCTOR_ID, null, 0, 20);

        EnrollmentProgressView item = page.data().getFirst();
        assertThat(item.progressPercent()).isEqualTo(67);
        assertThat(item.attendanceRate()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    private static EnrollmentProgressView view() {
        EnrolleeProfile profile = new EnrolleeProfile("학생1", null, null, null, null, null);
        return new EnrollmentProgressView(ENROLLMENT_ID, UUID.randomUUID(), profile, UUID.randomUUID(),
            "QA-C-BASIC", 2, 3, null, "APPROVED", null);
    }
}
