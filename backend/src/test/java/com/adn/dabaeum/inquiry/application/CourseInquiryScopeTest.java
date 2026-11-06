package com.adn.dabaeum.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import java.time.Instant;
import java.time.LocalDate;
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
 * GET /courses/{courseId}/inquiries 는 관리자(C-6)와 학습자(E-2)가 함께 쓰는 유일한 목록이라
 * "문의자 실명·연락처를 누구에게 보여줄지" 를 서비스가 정한다. 역할 이름만으로 정하면
 * 다른 기관 강사에게 남의 기관 문의자 개인정보가 그대로 나갔다 — 그 회귀를 막는 테스트다.
 *
 * <p>인가 규칙 자체를 검증하는 것이 요지라 AuthorizationPolicy 는 목이 아니라 진짜를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class CourseInquiryScopeTest {

    private static final UUID COURSE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final UUID OWNING_INSTITUTION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_INSTITUTION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    @Mock InquiryRepository inquiryRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository courseInstructorRepository;
    @Mock com.adn.dabaeum.user.domain.UserRepository userRepository;

    private DefaultInquiryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInquiryApplicationService(
            inquiryRepository, courseRepository, courseInstructorRepository,
            new AuthorizationPolicy(), userRepository);
        lenient().when(courseRepository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(course()));
        lenient().when(inquiryRepository.findByCourseId(eq(COURSE_ID), any(), anyInt(), anyInt()))
            .thenReturn(List.of(view()));
        lenient().when(inquiryRepository.countByCourseId(eq(COURSE_ID), any())).thenReturn(1L);
    }

    @Test
    void 다른_기관_강사에게는_관리자_권한을_주지_않는다() {
        // 강사 역할은 기관 한 곳의 승인만으로 얻는다. 역할 이름만 보면 여기서 남의 기관이 뚫린다.
        lenient().when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, ACTOR_ID))
            .thenReturn(false);

        var result = list(role("INSTRUCTOR", OTHER_INSTITUTION));

        assertThat(result.manager()).isFalse();
        assertThat(result.page().data()).hasSize(1);
    }

    @Test
    void 다른_기관_관리자에게도_주지_않는다() {
        var result = list(role("INSTITUTION_ADMIN", OTHER_INSTITUTION));

        assertThat(result.manager()).isFalse();
    }

    @Test
    void 강좌를_가진_기관의_관리자에게는_준다() {
        var result = list(role("INSTITUTION_ADMIN", OWNING_INSTITUTION));

        assertThat(result.manager()).isTrue();
    }

    @Test
    void 그_강좌에_배정된_같은_기관_강사에게는_준다() {
        when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, ACTOR_ID))
            .thenReturn(true);

        var result = list(role("INSTRUCTOR", OWNING_INSTITUTION));

        assertThat(result.manager()).isTrue();
    }

    @Test
    void 같은_기관이어도_배정되지_않은_강사에게는_주지_않는다() {
        when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, ACTOR_ID))
            .thenReturn(false);

        var result = list(role("INSTRUCTOR", OWNING_INSTITUTION));

        assertThat(result.manager()).isFalse();
    }

    @Test
    void 학습자는_목록을_받되_관리자는_아니다() {
        var result = list(role("LEARNER", null));

        assertThat(result.manager()).isFalse();
        assertThat(result.page().totalElements()).isEqualTo(1);
    }

    @Test
    void 플랫폼_관리자에게는_준다() {
        var result = list(role("PLATFORM_ADMIN", null));

        assertThat(result.manager()).isTrue();
    }

    private CourseInquiryViewPage list(AuthenticatedRole actorRole) {
        return service.listForCourse(
            new AuthenticatedUserContext(ACTOR_ID, "LOCAL", Set.of(actorRole)),
            COURSE_ID, null, 0, 20);
    }

    private static AuthenticatedRole role(String name, UUID institutionId) {
        return new AuthenticatedRole(name, institutionId);
    }

    private static Course course() {
        return new Course(COURSE_ID, OWNING_INSTITUTION, "C-001", "AI 기초", null, null,
            CourseEducationType.OFFLINE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            null, null, 30, null, null, false, null, CourseStatus.IN_PROGRESS, NOW, NOW, null);
    }

    private static InquiryView view() {
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", null, null, "seed.learner@dabaeum.kr", "010-1234-5678", null);
        return new InquiryView(
            UUID.randomUUID(), UUID.randomUUID(), COURSE_ID, "문의 제목", InquiryStatus.PENDING,
            InquiryType.COURSE, NOW, "AI 기초", profile, "다배움기관", "강사", "TSIX_010201", "OFFLINE",
            5, "태그", null, null, null);
    }
}
