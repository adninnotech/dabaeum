package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 횡단 이슈 X-2: 수강 신청 목록이 행마다 {@code /users/{id}} 를 다시 부르지 않고
 * 신청자 정보를 함께 받아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class EnrolleeProfileTest {

    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LEARNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock EnrollmentRepository enrollmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock com.adn.dabaeum.course.domain.CourseSessionRepository sessionRepository;
    @Mock EnrollmentIdGenerator idGenerator;

    private DefaultEnrollmentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultEnrollmentApplicationService(
            enrollmentRepository, courseRepository, userRepository, userRoleRepository,
            instructorRepository, idGenerator, new AuthorizationPolicy(),
            new EnrollmentDecisionAuthority(instructorRepository, new AuthorizationPolicy()),
            new EnrollmentWithdrawalPolicy(sessionRepository,
                new com.adn.dabaeum.enrollment.config.EnrollmentWithdrawalProperties(
                    java.time.Duration.ofHours(24))),
            Clock.fixed(NOW, ZoneOffset.UTC));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(java.util.Optional.of(course()));
    }

    @Test
    void 신청_목록이_회원정보를_한_번에_준다() {
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", LocalDate.of(1990, 3, 15), null,
            "seed.learner@dabaeum.kr", "010-1234-5678", null);
        when(enrollmentRepository.findEnrollmentListByCourseId(any()))
            .thenReturn(List.of(new EnrollmentListItem(enrollment(), profile, null, 0, null)));
        when(enrollmentRepository.countByCourseId(COURSE_ID)).thenReturn(1L);

        EnrollmentPage page = service.list(
            new ListCourseEnrollmentsQuery(COURSE_ID, 0, 10, "createdAt,desc"), adminContext());

        EnrolleeProfile actual = page.data().get(0).profile();
        assertThat(actual.userName()).isEqualTo("시드학습자");
        assertThat(actual.birthDate()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(actual.email()).isEqualTo("seed.learner@dabaeum.kr");
        assertThat(actual.phone()).isEqualTo("010-1234-5678");
    }

    @Test
    void 성별이_없으면_프로필의_성별도_null이다() {
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", LocalDate.of(1990, 3, 15), null, "e@e.com", "010", null);
        when(enrollmentRepository.findEnrollmentListByCourseId(any()))
            .thenReturn(List.of(new EnrollmentListItem(enrollment(), profile, null, 0, null)));
        when(enrollmentRepository.countByCourseId(COURSE_ID)).thenReturn(1L);

        EnrollmentPage page = service.list(
            new ListCourseEnrollmentsQuery(COURSE_ID, 0, 10, "createdAt,desc"), adminContext());

        assertThat(page.data().get(0).profile().gender()).isNull();
    }

    @Test
    void 다른_기관_관리자는_볼_수_없다() {
        assertThatThrownBy(() -> service.list(
            new ListCourseEnrollmentsQuery(COURSE_ID, 0, 10, "createdAt,desc"),
            adminContextOf(OTHER_INSTITUTION_ID)))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(403));
        verify(enrollmentRepository, never()).findEnrollmentListByCourseId(any());
    }

    @Test
    void 학습자는_남의_신청_목록을_볼_수_없다() {
        assertThatThrownBy(() -> service.list(
            new ListCourseEnrollmentsQuery(COURSE_ID, 0, 10, "createdAt,desc"), learnerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(403));
        verify(enrollmentRepository, never()).findEnrollmentListByCourseId(any());
    }

    @Test
    void 목록_한_번에_추가_쿼리가_붙지_않는다() {
        EnrolleeProfile profile = new EnrolleeProfile("이름", null, null, null, null, null);
        when(enrollmentRepository.findEnrollmentListByCourseId(any()))
            .thenReturn(List.of(new EnrollmentListItem(enrollment(), profile, null, 0, null)));
        when(enrollmentRepository.countByCourseId(COURSE_ID)).thenReturn(1L);

        service.list(new ListCourseEnrollmentsQuery(COURSE_ID, 0, 10, "createdAt,desc"), adminContext());

        verify(enrollmentRepository, times(1)).findEnrollmentListByCourseId(any());
        verify(enrollmentRepository, times(1)).countByCourseId(COURSE_ID);
    }

    private AuthenticatedUserContext adminContext() {
        return adminContextOf(INSTITUTION_ID);
    }

    private AuthenticatedUserContext adminContextOf(UUID institutionId) {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)));
    }

    private AuthenticatedUserContext learnerContext() {
        return new AuthenticatedUserContext(LEARNER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }

    private Enrollment enrollment() {
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, LEARNER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPLIED, NOW, null, null,
            null, null, null, null, NOW, NOW);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "COURSE-1", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, 10, null, null, false, (BigDecimal) null, CourseStatus.RECRUITING,
            NOW, NOW, null);
    }
}
