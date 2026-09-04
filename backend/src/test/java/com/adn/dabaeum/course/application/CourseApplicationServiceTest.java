package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CourseApplicationServiceTest {

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
    void createsDraftCourseWithGeneratedIdAndClock() {
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "COURSE-001"
        )).thenReturn(Optional.empty());

        Course created = service.create(command(), globalPlatformAdmin());

        assertThat(created.id()).isEqualTo(COURSE_ID);
        assertThat(created.institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(created.courseCode()).isEqualTo("COURSE-001");
        assertThat(created.title()).isEqualTo("과정 제목");
        assertThat(created.educationType()).isEqualTo(CourseEducationType.HYBRID);
        assertThat(created.status()).isEqualTo(CourseStatus.DRAFT);
        assertThat(created.createdAt()).isEqualTo(FIXED_INSTANT);
        assertThat(created.updatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(created.creditBankEligible()).isTrue();
        assertThat(created.creditValue()).isEqualByComparingTo("12.50");
        verify(repository).save(created);
    }

    @Test
    void trimsRequiredStringsAndDefaultsOmittedCreditBankFlag() {
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "COURSE-TRIM"
        )).thenReturn(Optional.empty());

        Course created = service.create(new CreateCourseCommand(
            INSTITUTION_ID,
            " COURSE-TRIM ",
            " 과정 제목 ",
            null,
            null,
            CourseEducationType.ONLINE,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 1),
            null,
            null,
            1,
            null,
            null,
            false,
            null
        ), globalPlatformAdmin());

        assertThat(created.courseCode()).isEqualTo("COURSE-TRIM");
        assertThat(created.title()).isEqualTo("과정 제목");
        assertThat(created.creditBankEligible()).isFalse();
    }

    @Test
    void rejectsActiveDuplicateCourseCodeBeforeSave() {
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "COURSE-001"
        )).thenReturn(Optional.of(course()));

        ApiException exception = catchThrowableOfType(
            () -> service.create(command(), globalPlatformAdmin()),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.COURSE_CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingInstitution() {
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
            () -> service.create(command(), globalPlatformAdmin()),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_NOT_FOUND);
        verify(repository, never()).save(any());
    }

    @Test
    void allowsGlobalAndSameInstitutionAdminButRejectsOtherScopes() {
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            eq(INSTITUTION_ID), eq("COURSE-001")
        )).thenReturn(Optional.empty());
        when(idGenerator.generate()).thenReturn(COURSE_ID);

        service.create(command(), institutionAdmin(INSTITUTION_ID));
        service.create(command(), globalPlatformAdmin());

        ApiException differentInstitution = catchThrowableOfType(
            () -> service.create(command(), institutionAdmin(OTHER_INSTITUTION_ID)),
            ApiException.class
        );
        ApiException learner = catchThrowableOfType(
            () -> service.create(command(), learner()),
            ApiException.class
        );

        assertThat(differentInstitution.code())
            .isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
        assertThat(learner.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void mapsSaveIntegrityViolationWithoutDatabaseMessage() {
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "COURSE-001"
        )).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("database detail"))
            .when(repository)
            .save(any());

        ApiException exception = catchThrowableOfType(
            () -> service.create(command(), globalPlatformAdmin()),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.COURSE_CONFLICT);
        assertThat(exception.getMessage()).doesNotContain("database detail");
    }

    @Test
    void getsActiveCourseAndMapsMissingToNotFound() {
        Course course = course();
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course));
        assertThat(service.get(COURSE_ID)).isSameAs(course);

        when(repository.findActiveById(INSTITUTION_ID)).thenReturn(Optional.empty());
        ApiException exception = catchThrowableOfType(
            () -> service.get(INSTITUTION_ID),
            ApiException.class
        );
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void rejectsInvalidCreateCommandAsValidationFailed() {
        for (CreateCourseCommand invalid : new CreateCourseCommand[] {
            null,
            new CreateCourseCommand(
                null, "CODE", "제목", null, null, CourseEducationType.ONLINE,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1),
                null, null, 1, null, null, false, null
            ),
            new CreateCourseCommand(
                INSTITUTION_ID, " ", "제목", null, null, CourseEducationType.ONLINE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                null, null, 1, null, null, false, null
            ),
            new CreateCourseCommand(
                INSTITUTION_ID, "CODE", "제목", null, null, CourseEducationType.ONLINE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                null, null, null, null, null, null, null
            )
        }) {
            ApiException exception = catchThrowableOfType(
                () -> service.create(invalid, globalPlatformAdmin()),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        }
    }

    private CreateCourseCommand command() {
        return new CreateCourseCommand(
            INSTITUTION_ID,
            "COURSE-001",
            "과정 제목",
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
            new BigDecimal("12.50")
        );
    }

    private Course course() {
        return new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "기존 과정",
            null,
            null,
            CourseEducationType.ONLINE,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 1),
            null,
            null,
            1,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
    }

    private Institution institution(UUID id) {
        return new Institution(
            id,
            "INST-" + id.toString().substring(0, 8),
            "기관",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
    }

    private AuthenticatedUserContext globalPlatformAdmin() {
        return context(new AuthenticatedRole("PLATFORM_ADMIN", null));
    }

    private AuthenticatedUserContext institutionAdmin(UUID institutionId) {
        return context(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId));
    }

    private AuthenticatedUserContext learner() {
        return context(new AuthenticatedRole("LEARNER", INSTITUTION_ID));
    }

    private AuthenticatedUserContext context(AuthenticatedRole role) {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(role)
        );
    }
}
