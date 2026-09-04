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
            COURSE_ID, 1, STARTS(), ENDS(), null, null, null, CourseSessionStatus.SCHEDULED
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
                COURSE_ID, 1, STARTS(), ENDS(), null, null, null, CourseSessionStatus.SCHEDULED
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
                COURSE_ID, 1, STARTS(), ENDS(), null, null, null, CourseSessionStatus.SCHEDULED
            ), globalAdmin()),
            ApiException.class
        );
        assertThat(duplicate.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);

        CourseSession completed = new CourseSession(
            SESSION_ID, COURSE_ID, 1, STARTS(), ENDS(), null, null, null,
            CourseSessionStatus.COMPLETED, NOW, NOW
        );
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(completed));
        ApiException finalState = catchThrowableOfType(
            () -> service.update(new UpdateCourseSessionCommand(
                SESSION_ID,
                CourseSessionUpdateField.present(2), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent(),
                CourseSessionUpdateField.absent()
            ), globalAdmin()),
            ApiException.class
        );
        assertThat(finalState.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
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
                CourseSessionUpdateField.absent(), CourseSessionUpdateField.absent()
            ), globalAdmin()),
            ApiException.class
        );
        assertThat(missing.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_NOT_FOUND);
    }

    private CourseSession session() {
        return new CourseSession(SESSION_ID, COURSE_ID, 1, STARTS(), ENDS(), "기존 장소",
            NOW.plusSeconds(60), NOW.plusSeconds(120), CourseSessionStatus.SCHEDULED, NOW, NOW);
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
}
