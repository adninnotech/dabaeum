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
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
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

    @Mock
    CourseInstructorRepository courseInstructorRepository;

    @Mock
    CourseInstructorIdGenerator courseInstructorIdGenerator;

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
            org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFileRepository.class),
            new SequentialCourseCodeGenerator(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)),
            courseInstructorRepository,
            courseInstructorIdGenerator,
            org.mockito.Mockito.mock(com.adn.dabaeum.classification.application.CourseClassificationService.class),
            java.util.List.of()
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
    void generatesCourseCodeWhenOmitted() {
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(eq(INSTITUTION_ID), any()))
            .thenReturn(Optional.empty());

        Course created = service.create(new CreateCourseCommand(
            INSTITUTION_ID,
            null,
            "과정 제목",
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
            true,
            null
        ), globalPlatformAdmin());

        assertThat(created.courseCode()).matches("C-\\d{6}-[0-9A-HJ-NP-Z]{6}");
    }

    @Test
    void usesGivenCourseCodeWhenPresent() {
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            INSTITUTION_ID,
            "GIVEN-CODE"
        )).thenReturn(Optional.empty());

        Course created = service.create(new CreateCourseCommand(
            INSTITUTION_ID,
            " GIVEN-CODE ",
            "과정 제목",
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
            true,
            null
        ), globalPlatformAdmin());

        assertThat(created.courseCode()).isEqualTo("GIVEN-CODE");
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
    void allowsSameInstitutionInstructorToCreateAndRejectsOtherInstitution() {
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            eq(INSTITUTION_ID), eq("COURSE-001")
        )).thenReturn(Optional.empty());
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        // 강사가 만들면 생성자를 MAIN 으로 자동 배정하므로 배정 id 생성기도 필요하다.
        when(courseInstructorIdGenerator.generate()).thenReturn(UUID.randomUUID());

        Course created = service.create(command(), instructor(INSTITUTION_ID));

        assertThat(created.id()).isEqualTo(COURSE_ID);

        ApiException otherInstitution = catchThrowableOfType(
            () -> service.create(command(), instructor(OTHER_INSTITUTION_ID)),
            ApiException.class
        );
        assertThat(otherInstitution.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(otherInstitution.code())
            .isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
    }

    @Test
    void assignsCreatorAsMainInstructorWhenInstructorCreatesCourse() {
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            eq(INSTITUTION_ID), eq("COURSE-001")
        )).thenReturn(Optional.empty());
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        UUID instructorAssignmentId = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
        );
        when(courseInstructorIdGenerator.generate()).thenReturn(instructorAssignmentId);
        AuthenticatedUserContext instructorContext = instructor(INSTITUTION_ID);

        service.create(command(), instructorContext);

        org.mockito.ArgumentCaptor<CourseInstructor> captor =
            org.mockito.ArgumentCaptor.forClass(CourseInstructor.class);
        verify(courseInstructorRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(instructorAssignmentId);
        assertThat(captor.getValue().courseId()).isEqualTo(COURSE_ID);
        assertThat(captor.getValue().userId()).isEqualTo(instructorContext.userId());
        assertThat(captor.getValue().role()).isEqualTo(CourseInstructorRole.MAIN);
    }

    @Test
    void doesNotAssignMainInstructorWhenInstitutionAdminCreatesCourse() {
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        when(repository.findActiveByInstitutionAndCode(
            eq(INSTITUTION_ID), eq("COURSE-001")
        )).thenReturn(Optional.empty());
        when(idGenerator.generate()).thenReturn(COURSE_ID);

        service.create(command(), institutionAdmin(INSTITUTION_ID));

        verify(courseInstructorRepository, never()).save(any());
    }

    @Test
    void instructorAssignedAsMainCanUpdateOwnCourseButNotAnothersOrPublish() {
        Course existing = course();
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(existing));
        when(repository.updateActive(any())).thenReturn(true);
        AuthenticatedUserContext instructorContext = instructor(INSTITUTION_ID);
        when(courseInstructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, instructorContext.userId(), CourseInstructorRole.MAIN
        )).thenReturn(true);

        Course updated = service.update(new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.absent(),
            CourseUpdateField.present("강사가 고친 제목"),
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
        ), instructorContext);
        assertThat(updated.title()).isEqualTo("강사가 고친 제목");

        ApiException publishDenied = catchThrowableOfType(
            () -> service.publish(COURSE_ID, instructorContext),
            ApiException.class
        );
        assertThat(publishDenied.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publishDenied.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
    }

    @Test
    void instructorNotAssignedAsMainCannotUpdateSameInstitutionCourse() {
        Course existing = course();
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(existing));
        AuthenticatedUserContext instructorContext = instructor(INSTITUTION_ID);
        when(courseInstructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, instructorContext.userId(), CourseInstructorRole.MAIN
        )).thenReturn(false);

        ApiException exception = catchThrowableOfType(
            () -> service.update(new UpdateCourseCommand(
                COURSE_ID,
                CourseUpdateField.absent(),
                CourseUpdateField.present("남의 강좌"),
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
            ), instructorContext),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
        verify(repository, never()).updateActive(any());
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
        CourseView view = new CourseView(course, 5, null, null);
        when(repository.findActiveViewById(COURSE_ID)).thenReturn(Optional.of(view));
        assertThat(service.get(COURSE_ID, institutionAdmin(INSTITUTION_ID))).isSameAs(view);

        when(repository.findActiveViewById(INSTITUTION_ID)).thenReturn(Optional.empty());
        ApiException exception = catchThrowableOfType(
            () -> service.get(INSTITUTION_ID, institutionAdmin(INSTITUTION_ID)),
            ApiException.class
        );
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void 공개된_강좌는_학습자도_볼_수_있다() {
        Course published = new Course(
            COURSE_ID, INSTITUTION_ID, "COURSE-001", "기존 과정", null, null,
            CourseEducationType.ONLINE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
            null, null, 1, null, null, false, null,
            CourseStatus.RECRUITING, FIXED_INSTANT, FIXED_INSTANT, null
        );
        CourseView view = new CourseView(published, 0, null, null);
        when(repository.findActiveViewById(COURSE_ID)).thenReturn(Optional.of(view));

        assertThat(service.get(COURSE_ID, learner())).isSameAs(view);
    }

    @Test
    void DRAFT는_그_기관_소속_INSTITUTION_ADMIN과_INSTRUCTOR에게_보인다() {
        Course course = course();
        CourseView view = new CourseView(course, 0, null, null);
        when(repository.findActiveViewById(COURSE_ID)).thenReturn(Optional.of(view));

        assertThat(service.get(COURSE_ID, institutionAdmin(INSTITUTION_ID))).isSameAs(view);
        assertThat(service.get(COURSE_ID, instructor(INSTITUTION_ID))).isSameAs(view);
        assertThat(service.get(COURSE_ID, globalPlatformAdmin())).isSameAs(view);
    }

    @Test
    void DRAFT는_다른_기관_직원과_학습자에게_COURSE_NOT_FOUND다() {
        Course course = course();
        CourseView view = new CourseView(course, 0, null, null);
        when(repository.findActiveViewById(COURSE_ID)).thenReturn(Optional.of(view));

        ApiException byOtherAdmin = catchThrowableOfType(
            () -> service.get(COURSE_ID, institutionAdmin(OTHER_INSTITUTION_ID)),
            ApiException.class
        );
        assertThat(byOtherAdmin.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(byOtherAdmin.code()).isEqualTo(ApiErrorCode.COURSE_NOT_FOUND);

        ApiException byLearner = catchThrowableOfType(
            () -> service.get(COURSE_ID, learner()),
            ApiException.class
        );
        assertThat(byLearner.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(byLearner.code()).isEqualTo(ApiErrorCode.COURSE_NOT_FOUND);
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
                INSTITUTION_ID, "C".repeat(51), "제목", null, null, CourseEducationType.ONLINE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                null, null, 1, null, null, false, null
            ),
            new CreateCourseCommand(
                INSTITUTION_ID, "CODE", "x".repeat(61), null, null, CourseEducationType.ONLINE,
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
            assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        }
    }

    @Test
    void 필수값이_빈_DRAFT_는_공개할_수_없다() {
        Course draft = new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "기존 과정",
            null,
            null,
            CourseEducationType.ONLINE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(draft));

        ApiException exception = catchThrowableOfType(
            () -> service.publish(COURSE_ID, globalPlatformAdmin()),
            ApiException.class
        );
        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(exception.details()).containsExactly("startDate", "endDate", "capacity", "classificationCode");
    }

    @Test
    void 일정과_정원이_빈_DRAFT_도_취소는_가능하다() {
        Course draft = new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "기존 과정",
            null,
            null,
            CourseEducationType.ONLINE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(draft));
        when(repository.updateActive(any())).thenReturn(true);

        Course updated = service.update(new UpdateCourseCommand(
            COURSE_ID,
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
            CourseUpdateField.absent(),
            CourseUpdateField.present(CourseStatus.CANCELLED)
        ), globalPlatformAdmin());

        assertThat(updated.status()).isEqualTo(CourseStatus.CANCELLED);
        assertThat(updated.startDate()).isNull();
        assertThat(updated.endDate()).isNull();
        assertThat(updated.capacity()).isNull();
    }

    @Test
    void update_은_상세설정을_지우지_않는다() {
        Course existing = course().withDetail(
            com.adn.dabaeum.course.domain.CourseDetail.empty().withTags("#유지되어야함"));
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(existing));
        when(repository.updateActive(any())).thenReturn(true);

        Course updated = service.update(new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.absent(),
            CourseUpdateField.present("새 제목"),
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
        ), globalPlatformAdmin());

        assertThat(updated.detail().tags()).isEqualTo("#유지되어야함");
    }

    @Test
    void publish_는_상세설정을_지우지_않는다() {
        Course existing = course().withDetail(
            com.adn.dabaeum.course.domain.CourseDetail.empty().withTags("#유지되어야함"));
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(existing));
        when(repository.updateActive(any())).thenReturn(true);

        Course published = service.publish(COURSE_ID, globalPlatformAdmin());

        assertThat(published.status()).isEqualTo(CourseStatus.RECRUITING);
        assertThat(published.detail().tags()).isEqualTo("#유지되어야함");
    }

    @Test
    void reopen_은_상세설정을_지우지_않는다() {
        Course existing = new Course(
            COURSE_ID, INSTITUTION_ID, "COURSE-001", "기존 과정", null, null,
            CourseEducationType.ONLINE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
            null, null, 1, null, null, false, null,
            CourseStatus.RECRUITMENT_CLOSED, FIXED_INSTANT, FIXED_INSTANT, null
        ).withDetail(com.adn.dabaeum.course.domain.CourseDetail.empty().withTags("#유지되어야함"));
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(existing));
        when(repository.updateActive(any())).thenReturn(true);

        Course reopened = service.reopen(COURSE_ID, "정정 사유", globalPlatformAdmin());

        assertThat(reopened.status()).isEqualTo(CourseStatus.RECRUITING);
        assertThat(reopened.detail().tags()).isEqualTo("#유지되어야함");
    }

    private CreateCourseCommand command() {
        return new CreateCourseCommand(
            INSTITUTION_ID,
            "COURSE-001",
            "과정 제목",
            "상세 설명",
            "TSIX_040201",
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

    @Test
    void 모집_마감일이_시작일보다_뒤면_생성을_422로_거부한다() {
        CreateCourseCommand base = command();
        CreateCourseCommand lateRecruitEnd = new CreateCourseCommand(
            base.institutionId(), base.courseCode(), base.title(), base.description(),
            base.classificationCode(), base.educationType(), base.startDate(), base.endDate(),
            base.recruitStartDate(), base.startDate().plusDays(1), base.capacity(), base.location(),
            base.onlineUrl(), base.creditBankEligible(), base.creditValue());

        ApiException exception = catchThrowableOfType(
            () -> service.create(lateRecruitEnd, globalPlatformAdmin()), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(exception.details()).containsExactly("recruitEndDate");
        verify(repository, never()).save(any());
    }

    @Test
    void 모집_마감일이_시작일과_같은_날이면_생성한다() {
        when(idGenerator.generate()).thenReturn(COURSE_ID);
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution(INSTITUTION_ID)));
        CreateCourseCommand base = command();
        CreateCourseCommand sameDay = new CreateCourseCommand(
            base.institutionId(), base.courseCode(), base.title(), base.description(),
            base.classificationCode(), base.educationType(), base.startDate(), base.endDate(),
            base.recruitStartDate(), base.startDate(), base.capacity(), base.location(),
            base.onlineUrl(), base.creditBankEligible(), base.creditValue());

        assertThat(service.create(sameDay, globalPlatformAdmin()).recruitEndDate())
            .isEqualTo(base.startDate());
    }

    @Test
    void 수정은_저장된_시작일과_비교해_모집_마감일이_뒤면_거부한다() {
        // 저장된 시작일 2026-09-01. 모집 마감일만 보내도 저장값과 비교한다(QA VAL-02).
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));

        ApiException onlyRecruitEnd = catchThrowableOfType(
            () -> service.update(datesUpdate(CourseUpdateField.absent(),
                CourseUpdateField.present(LocalDate.of(2026, 9, 2))), globalPlatformAdmin()),
            ApiException.class);
        ApiException onlyStart = catchThrowableOfType(
            () -> service.update(datesUpdateWithRecruitEnd(LocalDate.of(2026, 8, 31)), globalPlatformAdmin()),
            ApiException.class);

        assertThat(onlyRecruitEnd.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(onlyRecruitEnd.details()).containsExactly("recruitEndDate");
        assertThat(onlyStart.details()).containsExactly("recruitEndDate");
        verify(repository, never()).updateActive(any());
    }

    /** 저장된 모집 마감일이 있는 강좌의 시작일만 그보다 앞으로 당긴다. */
    private UpdateCourseCommand datesUpdateWithRecruitEnd(LocalDate storedRecruitEnd) {
        Course stored = course();
        Course withRecruitEnd = new Course(stored.id(), stored.institutionId(), stored.courseCode(), stored.title(),
            stored.description(), stored.classificationCode(), stored.educationType(), stored.startDate(),
            stored.endDate(), null, storedRecruitEnd, stored.capacity(), stored.location(), stored.onlineUrl(),
            stored.creditBankEligible(), stored.creditValue(), stored.status(), stored.createdAt(),
            stored.updatedAt(), stored.deletedAt());
        when(repository.findActiveById(COURSE_ID)).thenReturn(Optional.of(withRecruitEnd));
        return datesUpdate(CourseUpdateField.present(storedRecruitEnd.minusDays(1)), CourseUpdateField.absent());
    }

    private UpdateCourseCommand datesUpdate(
        CourseUpdateField<LocalDate> startDate, CourseUpdateField<LocalDate> recruitEndDate
    ) {
        return new UpdateCourseCommand(
            COURSE_ID,
            CourseUpdateField.absent(), CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(), startDate, CourseUpdateField.absent(),
            CourseUpdateField.absent(), recruitEndDate, CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent(), CourseUpdateField.absent(), CourseUpdateField.absent(),
            CourseUpdateField.absent());
    }

    private Course course() {
        return new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "기존 과정",
            null,
            "TSIX_040201",
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

    private AuthenticatedUserContext instructor(UUID institutionId) {
        return context(new AuthenticatedRole("INSTRUCTOR", institutionId));
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
