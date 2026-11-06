package com.adn.dabaeum.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.application.DefaultCourseQueryService;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.enrollment.application.DefaultEnrollmentQueryService;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.LearningActivity;
import com.adn.dabaeum.enrollment.domain.LearningSummary;
import com.adn.dabaeum.enrollment.domain.UserLearningStats;
import com.adn.dabaeum.inquiry.application.DefaultInquiryApplicationService;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * B-4 관리자 화면을 열면서 {@code /users/me/*} 조회를 userId 인자판으로 일반화했다.
 * me 메서드는 본문을 갖지 않고 userId 판을 그대로 부른다 — 본문이 복제되면 한쪽만 고쳐져
 * 관리자 화면과 내 화면의 집계가 갈라진다. 그 위임을 세 서비스에서 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class UserScopedQueryDelegationTest {

    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID MISSING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Mock EnrollmentQueryRepository enrollmentQueryRepository;
    @Mock CourseQueryRepository courseQueryRepository;
    @Mock InquiryRepository inquiryRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository courseInstructorRepository;
    @Mock UserRepository userRepository;

    private DefaultEnrollmentQueryService enrollmentQueryService;
    private DefaultCourseQueryService courseQueryService;
    private DefaultInquiryApplicationService inquiryService;

    @BeforeEach
    void setUp() {
        enrollmentQueryService = spy(new DefaultEnrollmentQueryService(
            enrollmentQueryRepository, new AuthorizationPolicy(), userRepository,
            org.mockito.Mockito.mock(com.adn.dabaeum.attendance.application.AttendanceMetricsQuery.class),
            java.time.Clock.systemUTC()));
        courseQueryService = spy(new DefaultCourseQueryService(
            courseQueryRepository, new AuthorizationPolicy(), userRepository));
        inquiryService = spy(new DefaultInquiryApplicationService(
            inquiryRepository, courseRepository, courseInstructorRepository,
            new AuthorizationPolicy(), userRepository));
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        lenient().when(userRepository.findById(MISSING_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 수강_목록_me_는_userId_판에_위임한다() {
        when(enrollmentQueryRepository.findMyEnrollments(
            eq(USER_ID), any(), anyInt(), anyInt(), any())).thenReturn(List.of());
        when(enrollmentQueryRepository.countMyEnrollments(eq(USER_ID), any())).thenReturn(0L);

        enrollmentQueryService.listMyEnrollments(learner(), null, 0, 20, "appliedAt,desc");

        verify(enrollmentQueryService)
            .listEnrollmentsOf(USER_ID, null, 0, 20, "appliedAt,desc");
    }

    @Test
    void 강의_목록과_학습_요약_me_도_위임한다() {
        when(enrollmentQueryRepository.findLearningCourses(
            eq(USER_ID), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(enrollmentQueryRepository.countLearningCourses(eq(USER_ID), any(), any()))
            .thenReturn(0L);
        when(enrollmentQueryRepository.learningSummary(USER_ID))
            .thenReturn(new LearningSummary(1, 2, 4));

        enrollmentQueryService.listLearningCourses(learner(), null, null, 0, 20);
        enrollmentQueryService.learningSummary(learner());

        verify(enrollmentQueryService).listLearningCoursesOf(USER_ID, null, null, 0, 20);
        verify(enrollmentQueryService).learningSummaryOf(USER_ID);
    }

    @Test
    void 문의_목록_me_도_위임한다() {
        when(inquiryRepository.findByUserId(eq(USER_ID), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of());
        when(inquiryRepository.countByUserId(eq(USER_ID), any(), any())).thenReturn(0L);

        inquiryService.listMine(learner(), null, null, 0, 20);

        verify(inquiryService).listOf(USER_ID, null, null, 0, 20);
    }

    @Test
    void 강사_과정과_통계_me_도_위임한다() {
        when(courseQueryRepository.findInstructorCourses(
            eq(USER_ID), any(), anyInt(), anyInt(), any())).thenReturn(List.of());
        when(courseQueryRepository.countInstructorCourses(eq(USER_ID), any())).thenReturn(0L);
        when(courseQueryRepository.instructorCourseStats(USER_ID))
            .thenReturn(new InstructorCourseStats(0, 0, 0, 0, 0, 0));

        courseQueryService.listInstructorCourses(learner(), null, 0, 20, "createdAt,desc");
        courseQueryService.instructorCourseStats(learner());

        verify(courseQueryService)
            .listInstructorCoursesOf(USER_ID, null, 0, 20, "createdAt,desc");
        verify(courseQueryService).instructorCourseStatsOf(USER_ID);
    }

    @Test
    void 학습_통계의_전체_건수는_요약_세_값의_합이다() {
        when(enrollmentQueryRepository.learningSummary(USER_ID))
            .thenReturn(new LearningSummary(1, 2, 4));
        when(enrollmentQueryRepository.learningActivity(USER_ID))
            .thenReturn(new LearningActivity(540, 62, 3));

        UserLearningStats stats = enrollmentQueryService.learningStatsOf(USER_ID);

        assertThat(stats.totalCount()).isEqualTo(7);
        assertThat(stats.inProgressCount()).isEqualTo(2);
        assertThat(stats.completedCount()).isEqualTo(4);
        assertThat(stats.totalStudyMinutes()).isEqualTo(540);
        assertThat(stats.averageProgressPercent()).isEqualTo(62);
        assertThat(stats.inquiryCount()).isEqualTo(3);
    }

    @Test
    void 승인된_수강이_없으면_평균_진도율은_0_이다() {
        when(enrollmentQueryRepository.learningSummary(USER_ID))
            .thenReturn(new LearningSummary(0, 0, 0));
        when(enrollmentQueryRepository.learningActivity(USER_ID))
            .thenReturn(new LearningActivity(0, 0, 0));

        UserLearningStats stats = enrollmentQueryService.learningStatsOf(USER_ID);

        assertThat(stats.totalCount()).isZero();
        assertThat(stats.averageProgressPercent()).isZero();
    }

    @Test
    void 없는_사용자를_조회하면_404_다() {
        assertThatThrownBy(() -> enrollmentQueryService.learningStatsOf(MISSING_ID))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND));
        assertThatThrownBy(() -> inquiryService.listOf(MISSING_ID, null, null, 0, 20))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() ->
            courseQueryService.instructorCourseStatsOf(MISSING_ID))
            .isInstanceOf(ApiException.class);
    }

    private User user() {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        return new User(USER_ID, "시드학습자", "seed.learner@dabaeum.kr", null, null,
            UserStatus.ACTIVE, null, now, now);
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
