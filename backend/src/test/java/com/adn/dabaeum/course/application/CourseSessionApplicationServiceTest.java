package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionPageCriteria;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionSort;
import com.adn.dabaeum.course.domain.CourseSessionSortDirection;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CourseSessionApplicationServiceTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID SESSION_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999"
    );
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock CourseSessionRepository sessionRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock CourseSessionIdGenerator idGenerator;

    private CourseSessionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCourseSessionApplicationService(
            sessionRepository,
            courseRepository,
            instructorRepository,
            idGenerator,
            new AuthorizationPolicy(),
            org.mockito.Mockito.mock(com.adn.dabaeum.correction.domain.AdminCorrectionRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listsOnlyAfterCourseExistsAndMapsPageSort() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(sessionRepository.findPageByCourseId(any())).thenReturn(List.of(session()));
        when(sessionRepository.countByCourseId(COURSE_ID)).thenReturn(1L);

        CourseSessionPage result = service.list(new ListCourseSessionsQuery(
            COURSE_ID, 0, 20, "startsAt,asc"
        ));

        assertThat(result.data()).containsExactly(session());
        assertThat(result.totalPages()).isEqualTo(1);
        org.mockito.ArgumentCaptor<CourseSessionPageCriteria> criteria =
            org.mockito.ArgumentCaptor.forClass(CourseSessionPageCriteria.class);
        verify(sessionRepository).findPageByCourseId(criteria.capture());
        assertThat(criteria.getValue().sort()).isEqualTo(CourseSessionSort.STARTS_AT);
        assertThat(criteria.getValue().direction()).isEqualTo(CourseSessionSortDirection.ASC);
    }

    @Test
    void rejectsMissingCourseAndInvalidQuery() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.empty());
        ApiException missing = catchThrowableOfType(
            () -> service.list(new ListCourseSessionsQuery(COURSE_ID, 0, 20, "startsAt,asc")),
            ApiException.class
        );
        assertThat(missing.code()).isEqualTo(ApiErrorCode.COURSE_NOT_FOUND);

        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        ApiException invalid = catchThrowableOfType(
            () -> service.list(new ListCourseSessionsQuery(COURSE_ID, -1, 20, "startsAt,asc")),
            ApiException.class
        );
        assertThat(invalid.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void createsWithManagerScopeAndDefaultStatus() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(idGenerator.generate()).thenReturn(SESSION_ID);
        when(sessionRepository.findByCourseIdAndSessionNo(COURSE_ID, 1))
            .thenReturn(Optional.empty());
        CourseSession created = service.create(new CreateCourseSessionCommand(
            COURSE_ID, 1, STARTS(), ENDS(), null, null, null, null, CourseSessionStatus.SCHEDULED
        ), globalAdmin());

        assertThat(created.id()).isEqualTo(SESSION_ID);
        assertThat(created.status()).isEqualTo(CourseSessionStatus.SCHEDULED);
        verify(sessionRepository).save(created);
        verify(instructorRepository, never())
            .existsByCourseIdAndUserIdAndRole(any(), any(), any());
    }

    @Test
    void requiresMainInstructorForScopedInstructorAndMapsDuplicate() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, USER_ID, CourseInstructorRole.MAIN)).thenReturn(true);
        when(idGenerator.generate()).thenReturn(SESSION_ID);
        when(sessionRepository.findByCourseIdAndSessionNo(COURSE_ID, 1))
            .thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("duplicate"))
            .when(sessionRepository).save(any());

        ApiException conflict = catchThrowableOfType(
            () -> service.create(new CreateCourseSessionCommand(
                COURSE_ID, 1, STARTS(), ENDS(), null, null, null, null, CourseSessionStatus.SCHEDULED
            ), instructor()),
            ApiException.class
        );
        assertThat(conflict.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
        assertThat(conflict.getMessage()).doesNotContain("duplicate");
    }

    @Test
    void rejectsDuplicateAndFinalUpdate() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(sessionRepository.findByCourseIdAndSessionNo(COURSE_ID, 1))
            .thenReturn(Optional.of(session()));
        ApiException duplicate = catchThrowableOfType(
            () -> service.create(new CreateCourseSessionCommand(
                COURSE_ID, 1, STARTS(), ENDS(), null, null, null, null, CourseSessionStatus.SCHEDULED
            ), globalAdmin()),
            ApiException.class
        );
        assertThat(duplicate.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);

        CourseSession completed = new CourseSession(
            SESSION_ID, COURSE_ID, 1, STARTS(), ENDS(), null, null, null,
            CourseSessionStatus.COMPLETED, NOW, NOW, null
        );
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(completed));
        ApiException finalState = catchThrowableOfType(
            () -> service.update(new UpdateCourseSessionCommand(
                SESSION_ID,
                CourseSessionUpdateField.present(2), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent()
            ), globalAdmin()),
            ApiException.class
        );
        assertThat(finalState.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
    }

    @Test
    void 반복규칙_펼친_회차를_한_트랜잭션에서_만든다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(idGenerator.generate()).thenReturn(SESSION_ID);
        when(sessionRepository.findByCourseIdAndSessionNo(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(Optional.empty());

        List<CourseSession> created = service.createBulk(COURSE_ID,
            new CreateCourseSessionBulkCommand(List.of(
                new CreateCourseSessionCommand(COURSE_ID, 1, STARTS(), ENDS(),
                    null, null, null, null, CourseSessionStatus.SCHEDULED),
                new CreateCourseSessionCommand(COURSE_ID, 2, STARTS(), ENDS(),
                    null, null, null, null, CourseSessionStatus.SCHEDULED)
            ), false), globalAdmin());

        assertThat(created).hasSize(2);
        verify(sessionRepository, org.mockito.Mockito.times(2)).save(any());
        verify(sessionRepository, never()).existsSessionDependenciesByCourseId(any());
        verify(sessionRepository, never()).deleteAllByCourseId(any());
    }

    @Test
    void 반복규칙을_기존_회차_뒤에_붙이면_마지막_번호_다음부터_매긴다() {
        // 반복 규칙은 1부터 펼쳐진다. 지우지 않고 붙이면 기존 1·2회차와 번호가 겹쳐 409 였다(QA CRS-10).
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(idGenerator.generate()).thenReturn(SESSION_ID);
        when(sessionRepository.findByCourseId(COURSE_ID)).thenReturn(List.of(
            sessionNo(1), sessionNo(2)));
        when(sessionRepository.findByCourseIdAndSessionNo(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(Optional.empty());

        List<CourseSession> created = service.createBulk(COURSE_ID,
            CreateCourseSessionBulkCommand.appendingRepeat(List.of(
                new CreateCourseSessionCommand(COURSE_ID, 1, STARTS(), ENDS(),
                    null, null, null, null, CourseSessionStatus.SCHEDULED),
                new CreateCourseSessionCommand(COURSE_ID, 2, STARTS(), ENDS(),
                    null, null, null, null, CourseSessionStatus.SCHEDULED)
            )), globalAdmin());

        assertThat(created).extracting(CourseSession::sessionNo).containsExactly(3, 4);
        verify(sessionRepository, never()).deleteAllByCourseId(any());
    }

    @Test
    void 다른_기관_강사는_일괄생성을_할_수_없다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));

        ApiException forbidden = catchThrowableOfType(
            () -> service.createBulk(COURSE_ID,
                new CreateCourseSessionBulkCommand(List.of(
                    new CreateCourseSessionCommand(COURSE_ID, 1, STARTS(), ENDS(),
                        null, null, null, null, CourseSessionStatus.SCHEDULED)
                ), false),
                otherInstitutionInstructor()),
            ApiException.class
        );

        assertThat(forbidden.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void 출결이_기록된_회차가_있으면_재생성을_409로_거부한다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsSessionDependenciesByCourseId(COURSE_ID)).thenReturn(true);

        ApiException conflict = catchThrowableOfType(
            () -> service.createBulk(COURSE_ID,
                new CreateCourseSessionBulkCommand(List.of(
                    new CreateCourseSessionCommand(COURSE_ID, 1, STARTS(), ENDS(),
                        null, null, null, null, CourseSessionStatus.SCHEDULED)
                ), true),
                globalAdmin()),
            ApiException.class
        );

        assertThat(conflict.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
        verify(sessionRepository, never()).deleteAllByCourseId(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void SCHEDULED가_아닌_회차가_있으면_재생성을_409로_거부한다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsSessionDependenciesByCourseId(COURSE_ID)).thenReturn(false);
        when(sessionRepository.existsNonScheduledByCourseId(COURSE_ID)).thenReturn(true);

        ApiException conflict = catchThrowableOfType(
            () -> service.createBulk(COURSE_ID,
                new CreateCourseSessionBulkCommand(List.of(
                    new CreateCourseSessionCommand(COURSE_ID, 1, STARTS(), ENDS(),
                        null, null, null, null, CourseSessionStatus.SCHEDULED)
                ), true),
                globalAdmin()),
            ApiException.class
        );

        assertThat(conflict.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
        verify(sessionRepository, never()).deleteAllByCourseId(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void 배치의_회차번호가_기존회차와_겹치면_409로_거부한다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(idGenerator.generate()).thenReturn(SESSION_ID);
        when(sessionRepository.findByCourseIdAndSessionNo(COURSE_ID, 1))
            .thenReturn(Optional.empty());
        when(sessionRepository.findByCourseIdAndSessionNo(COURSE_ID, 2))
            .thenReturn(Optional.of(session()));

        ApiException conflict = catchThrowableOfType(
            () -> service.createBulk(COURSE_ID,
                new CreateCourseSessionBulkCommand(List.of(
                    new CreateCourseSessionCommand(COURSE_ID, 1, STARTS(), ENDS(),
                        null, null, null, null, CourseSessionStatus.SCHEDULED),
                    new CreateCourseSessionCommand(COURSE_ID, 2, STARTS(), ENDS(),
                        null, null, null, null, CourseSessionStatus.SCHEDULED)
                ), false), globalAdmin()),
            ApiException.class
        );

        assertThat(conflict.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
    }

    @Test
    void updatesPresenceAwareAndMapsMissingSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(sessionRepository.update(any())).thenReturn(true);

        CourseSession updated = service.update(new UpdateCourseSessionCommand(
            SESSION_ID,
            CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
            CourseSessionUpdateField.absent(), CourseSessionUpdateField.present("변경 장소"),
            CourseSessionUpdateField.absent(),
            CourseSessionUpdateField.present(null), CourseSessionUpdateField.present(null),
            CourseSessionUpdateField.present(CourseSessionStatus.OPEN)
        ), globalAdmin());

        assertThat(updated.location()).isEqualTo("변경 장소");
        assertThat(updated.attendanceOpensAt()).isNull();
        assertThat(updated.status()).isEqualTo(CourseSessionStatus.OPEN);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());
        ApiException missing = catchThrowableOfType(
            () -> service.update(new UpdateCourseSessionCommand(
                SESSION_ID, CourseSessionUpdateField.present(2),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent()
            ), globalAdmin()),
            ApiException.class
        );
        assertThat(missing.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_NOT_FOUND);
    }

    @Test
    void reopensACompletedSessionThroughTheCorrectionTransition() {
        CourseSession completed = new CourseSession(SESSION_ID, COURSE_ID, 1, STARTS(), ENDS(), "기존 장소",
            null, null, CourseSessionStatus.COMPLETED, NOW, NOW, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(completed));
        when(sessionRepository.reopen(SESSION_ID, CourseSessionStatus.COMPLETED, CourseSessionStatus.OPEN, NOW))
            .thenReturn(true);

        CourseSession reopened = service.reopen(SESSION_ID, "출결 정정", globalAdmin());

        assertThat(reopened.status()).isEqualTo(CourseSessionStatus.OPEN);
        // 일반 update 는 종료 회차를 0행으로 끝내 404 가 났다(QA CRS-12).
        verify(sessionRepository, never()).update(any());
    }

    private CourseSession sessionNo(int number) {
        return new CourseSession(UUID.randomUUID(), COURSE_ID, number, STARTS(), ENDS(), null,
            null, null, CourseSessionStatus.SCHEDULED, NOW, NOW, null);
    }

    private CourseSession session() {
        return new CourseSession(SESSION_ID, COURSE_ID, 1, STARTS(), ENDS(), "기존 장소",
            NOW.plusSeconds(60), NOW.plusSeconds(120), CourseSessionStatus.SCHEDULED, NOW, NOW,
            null);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "COURSE-001", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, 10, null, null, false, null, CourseStatus.DRAFT, NOW, NOW, null);
    }

    private Instant STARTS() { return NOW.plusSeconds(3600); }
    private Instant ENDS() { return NOW.plusSeconds(7200); }

    private AuthenticatedUserContext globalAdmin() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext otherInstitutionInstructor() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", OTHER_INSTITUTION_ID)));
    }
}
