package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseSessionReorderTest {

    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999");
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    private final UUID courseId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID otherCourseSessionId = UUID.randomUUID();

    @Mock CourseSessionRepository repository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock CourseSessionIdGenerator idGenerator;

    private CourseSessionApplicationService service;
    private List<CourseSession> sessions;
    private AuthenticatedUserContext managerContext;

    @BeforeEach
    void setUp() {
        service = new DefaultCourseSessionApplicationService(
            repository,
            courseRepository,
            instructorRepository,
            idGenerator,
            new AuthorizationPolicy(),
            org.mockito.Mockito.mock(
                com.adn.dabaeum.correction.domain.AdminCorrectionRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        sessions = List.of(session(1), session(2), session(3));
        managerContext = new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));

        // sessionIds 형식 검증(null 원소·중복 등)은 강좌 조회 전에 끝나는 시나리오가 있어 lenient 로 둔다.
        org.mockito.Mockito.lenient()
            .when(courseRepository.findActiveById(courseId)).thenReturn(Optional.of(course()));
        // 재정렬 실패 전 권한 검사에서 끝나는 시나리오도 있어 lenient 로 둔다.
        org.mockito.Mockito.lenient().when(repository.findByCourseId(courseId)).thenReturn(sessions);
        // 정상 경로에서 행 수 검증을 통과시키기 위한 기본 스텁. 실패 시나리오는 테스트별로 덮어쓴다.
        org.mockito.Mockito.lenient()
            .when(repository.shiftSessionNoByOffset(eq(courseId), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(sessions.size());
        org.mockito.Mockito.lenient()
            .when(repository.updateSessionNo(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(true);
    }

    @Test
    void 보낸_순서대로_회차번호가_다시_매겨진다() {
        UUID a = sessions.get(0).id();
        UUID b = sessions.get(1).id();
        UUID c = sessions.get(2).id();

        when(repository.findByCourseId(courseId))
            .thenReturn(sessions)
            .thenReturn(List.of(
                withSessionNo(sessions.get(2), 1),
                withSessionNo(sessions.get(0), 2),
                withSessionNo(sessions.get(1), 3)
            ));

        List<CourseSession> result = service.reorder(
            courseId, List.of(c, a, b), managerContext);

        assertThat(result)
            .extracting(CourseSession::id, CourseSession::sessionNo)
            .containsExactly(tuple(c, 1), tuple(a, 2), tuple(b, 3));

        verify(repository).shiftSessionNoByOffset(eq(courseId), eq(10_000));
        verify(repository).updateSessionNo(c, 1);
        verify(repository).updateSessionNo(a, 2);
        verify(repository).updateSessionNo(b, 3);
        verify(repository, times(3)).updateSessionNo(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 일부만_보내면_422() {
        assertThatThrownBy(() ->
            service.reorder(courseId, List.of(sessions.get(0).id()), managerContext))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("sessionIds");
    }

    @Test
    void sessionIds에_null원소가_있으면_422() {
        UUID a = sessions.get(0).id();

        ApiException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.reorder(
                courseId, java.util.Arrays.asList(a, null, sessions.get(2).id()), managerContext),
            ApiException.class);

        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(exception.details()).contains("sessionIds");
    }

    @Test
    void 중복된_id_를_보내면_422() {
        UUID a = sessions.get(0).id();

        assertThatThrownBy(() -> service.reorder(courseId, List.of(a, a, a), managerContext))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 다른_강좌의_회차_id_가_섞이면_422() {
        assertThatThrownBy(() -> service.reorder(
            courseId, List.of(sessions.get(0).id(), sessions.get(1).id(), otherCourseSessionId),
            managerContext))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void updateSessionNo_가_0행이면_조용히_넘어가지_않고_예외를_던진다() {
        UUID a = sessions.get(0).id();
        UUID b = sessions.get(1).id();
        UUID c = sessions.get(2).id();

        when(repository.updateSessionNo(eq(c), eq(1))).thenReturn(false);

        assertThatThrownBy(() -> service.reorder(courseId, List.of(c, a, b), managerContext))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void shiftSessionNoByOffset_이_스냅샷_크기와_다르면_예외를_던진다() {
        UUID a = sessions.get(0).id();
        UUID b = sessions.get(1).id();
        UUID c = sessions.get(2).id();

        when(repository.shiftSessionNoByOffset(eq(courseId), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(sessions.size() - 1);

        assertThatThrownBy(() -> service.reorder(courseId, List.of(c, a, b), managerContext))
            .isInstanceOf(ApiException.class);
        verify(repository, org.mockito.Mockito.never())
            .updateSessionNo(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 다른_기관_강사는_재정렬을_할_수_없다() {
        AuthenticatedUserContext otherInstructor = new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("INSTRUCTOR", OTHER_INSTITUTION_ID)));

        ApiException forbidden = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.reorder(courseId,
                List.of(sessions.get(0).id(), sessions.get(1).id(), sessions.get(2).id()),
                otherInstructor),
            ApiException.class);

        assertThat(forbidden.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
        verify(repository, org.mockito.Mockito.never())
            .shiftSessionNoByOffset(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private CourseSession session(int sessionNo) {
        Instant starts = NOW.plusSeconds(3600);
        Instant ends = NOW.plusSeconds(7200);
        return new CourseSession(UUID.randomUUID(), courseId, sessionNo, starts, ends,
            "기존 장소", null, null, CourseSessionStatus.SCHEDULED, NOW, NOW, null);
    }

    private CourseSession withSessionNo(CourseSession original, int sessionNo) {
        return new CourseSession(original.id(), original.courseId(), sessionNo,
            original.startsAt(), original.endsAt(), original.location(),
            original.attendanceOpensAt(), original.attendanceClosesAt(), original.status(),
            original.createdAt(), original.updatedAt(), original.topic());
    }

    private Course course() {
        return new Course(courseId, INSTITUTION_ID, "COURSE-001", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, 10, null, null, false, null, CourseStatus.DRAFT, NOW, NOW, null);
    }
}
