package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSort;
import com.adn.dabaeum.course.domain.CourseSortDirection;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CourseListUpdateLifecycleServiceTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );
    private static final Instant FIXED_INSTANT =
        Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    CourseRepository repository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    CourseIdGenerator idGenerator;

    private CourseApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCourseApplicationService(
            repository,
            institutionRepository,
            idGenerator,
            new AuthorizationPolicy(),
            org.mockito.Mockito.mock(com.adn.dabaeum.correction.domain.AdminCorrectionRepository.class),
            Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
            org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFileRepository.class)
        );
    }

    @Test
    void listsCoursesWithParsedSortAndPageMetadata() {
        Course listed = course(CourseStatus.DRAFT);
        when(repository.findActivePage(any())).thenReturn(List.of(listed));
        when(repository.countActive()).thenReturn(21L);

        CoursePage result = service.list(new ListCoursesQuery(
            1,
            20,
            "courseCode,asc"
        ));

        assertThat(result.data()).containsExactly(listed);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);

        ArgumentCaptor<CoursePageCriteria> criteria =
            ArgumentCaptor.forClass(CoursePageCriteria.class);
        verify(repository).findActivePage(criteria.capture());
        assertThat(criteria.getValue().offset()).isEqualTo(20);
        assertThat(criteria.getValue().limit()).isEqualTo(20);
        assertThat(criteria.getValue().sort()).isEqualTo(CourseSort.COURSE_CODE);
        assertThat(criteria.getValue().direction())
            .isEqualTo(CourseSortDirection.ASC);
    }

    @Test
    void rejectsInvalidListQueryAndOffsetOverflow() {
        for (ListCoursesQuery invalid : new ListCoursesQuery[] {
            null,
            new ListCoursesQuery(-1, 20, "createdAt,desc"),
            new ListCoursesQuery(0, 0, "createdAt,desc"),
            new ListCoursesQuery(0, 101, "createdAt,desc"),
            new ListCoursesQuery(0, 20, "unknown,desc"),
            new ListCoursesQuery(0, 20, "createdAt,sideways"),
            new ListCoursesQuery(Integer.MAX_VALUE, 100, "createdAt,desc")
        }) {
            ApiException exception = catchThrowableOfType(
                () -> service.list(invalid),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
        }
    }

    @Test
    void appliesPartialUpdateAndPreservesOmittedFields() {
        Course original = course(CourseStatus.DRAFT);
        when(repository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(original));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "COURSE-UPDATED"
        )).thenReturn(Optional.empty());
        when(repository.updateActive(any())).thenReturn(true);

        Course updated = service.update(new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.present(" COURSE-UPDATED "),
            CourseUpdateField.present("새 제목"),
            CourseUpdateField.present(null),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent(),
            CourseUpdateField.absent()
        ), sameInstitutionAdmin());

        assertThat(updated.courseCode()).isEqualTo("COURSE-UPDATED");
        assertThat(updated.title()).isEqualTo("새 제목");
        assertThat(updated.description()).isNull();
        assertThat(updated.category()).isEqualTo(original.category());
        assertThat(updated.capacity()).isEqualTo(original.capacity());
        assertThat(updated.status()).isEqualTo(CourseStatus.DRAFT);
        assertThat(updated.updatedAt()).isEqualTo(FIXED_INSTANT);
        verify(repository).updateActive(updated);
    }

    @Test
    void rejectsEmptyUpdateAndNullNonNullableStatus() {
        ApiException empty = catchThrowableOfType(
            () -> service.update(allAbsent(), sameInstitutionAdmin()),
            ApiException.class
        );
        assertThat(empty.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        Course original = course(CourseStatus.DRAFT);
        when(repository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(original));
        ApiException nullStatus = catchThrowableOfType(
            () -> service.update(commandWithStatus(null), sameInstitutionAdmin()),
            ApiException.class
        );
        assertThat(nullStatus.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(repository, never()).updateActive(any());
    }

    @Test
    void rejectsStatusShortcutAndFinalCourseMutation() {
        when(repository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(course(CourseStatus.DRAFT)));
        ApiException shortcut = catchThrowableOfType(
            () -> service.update(
                commandWithStatus(CourseStatus.RECRUITING),
                sameInstitutionAdmin()
            ),
            ApiException.class
        );
        assertThat(shortcut.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(shortcut.code()).isEqualTo(ApiErrorCode.COURSE_STATUS_CONFLICT);

        when(repository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(course(CourseStatus.COMPLETED)));
        ApiException finalState = catchThrowableOfType(
            () -> service.update(titleOnly("변경"), sameInstitutionAdmin()),
            ApiException.class
        );
        assertThat(finalState.code()).isEqualTo(ApiErrorCode.COURSE_STATUS_CONFLICT);
    }

    @Test
    void publishesAndClosesOnlyFromExpectedStates() {
        when(repository.findActiveById(COURSE_ID)).thenReturn(
            Optional.of(course(CourseStatus.DRAFT)),
            Optional.of(course(CourseStatus.RECRUITING)),
            Optional.of(course(CourseStatus.DRAFT))
        );
        when(repository.updateActive(any())).thenReturn(true);

        assertThat(service.publish(COURSE_ID, sameInstitutionAdmin()).status())
            .isEqualTo(CourseStatus.RECRUITING);
        assertThat(service.close(COURSE_ID, sameInstitutionAdmin()).status())
            .isEqualTo(CourseStatus.RECRUITMENT_CLOSED);

        ApiException invalidClose = catchThrowableOfType(
            () -> service.close(COURSE_ID, sameInstitutionAdmin()),
            ApiException.class
        );
        assertThat(invalidClose.code()).isEqualTo(ApiErrorCode.COURSE_STATUS_CONFLICT);
    }

    @Test
    void mapsUpdateDuplicateAndIntegrityErrorsToCourseConflict() {
        Course original = course(CourseStatus.DRAFT);
        when(repository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(original));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "DUPLICATE"
        )).thenReturn(Optional.of(course(CourseStatus.DRAFT)));

        ApiException duplicate = catchThrowableOfType(
            () -> service.update(new UpdateCourseCommand(
                COURSE_ID,
                CourseUpdateField.present("DUPLICATE"),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent()
            ), sameInstitutionAdmin()),
            ApiException.class
        );
        assertThat(duplicate.code()).isEqualTo(ApiErrorCode.COURSE_CONFLICT);

        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "COURSE-INTEGRITY"
        )).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("database detail"))
            .when(repository).updateActive(any());
        ApiException integrity = catchThrowableOfType(
            () -> service.update(new UpdateCourseCommand(
                COURSE_ID,
                CourseUpdateField.present("COURSE-INTEGRITY"),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent(),
                CourseUpdateField.absent()
            ), sameInstitutionAdmin()),
            ApiException.class
        );
        assertThat(integrity.code()).isEqualTo(ApiErrorCode.COURSE_CONFLICT);
        assertThat(integrity.getMessage()).doesNotContain("database detail");
    }

    @Test
    void rejectsManagerFromAnotherInstitution() {
        when(repository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(course(CourseStatus.DRAFT)));

        ApiException exception = catchThrowableOfType(
            () -> service.update(titleOnly("변경"), otherInstitutionAdmin()),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.code()).isEqualTo(
            ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
        verify(repository, never()).updateActive(any());
    }

    private UpdateCourseCommand allAbsent() {
        return new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent()
        );
    }

    private UpdateCourseCommand titleOnly(String title) {
        return new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.absent(), CourseUpdateField.present(title),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent()
        );
    }

    private UpdateCourseCommand commandWithStatus(CourseStatus status) {
        return new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.present(status)
        );
    }

    private Course course(CourseStatus status) {
        return new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "기존 과정",
            "상세 설명",
            "개발",
            CourseEducationType.HYBRID,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 25),
            30,
            "서울 교육장",
            "https://example.test/course",
            true,
            new BigDecimal("12.50"),
            status,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
    }

    private AuthenticatedUserContext sameInstitutionAdmin() {
        return context(new AuthenticatedRole(
            "INSTITUTION_ADMIN",
            INSTITUTION_ID
        ));
    }

    private AuthenticatedUserContext otherInstitutionAdmin() {
        return context(new AuthenticatedRole(
            "INSTITUTION_ADMIN",
            OTHER_INSTITUTION_ID
        ));
    }

    private AuthenticatedUserContext context(AuthenticatedRole role) {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(role)
        );
    }
}
