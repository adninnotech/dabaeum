package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseAttachment;
import com.adn.dabaeum.course.domain.CourseAttachmentRepository;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DefaultCourseAttachmentApplicationServiceTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999");
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID FILE_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");
    private static final UUID ATTACHMENT_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock CourseAttachmentRepository attachmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock StoredFileRepository fileRepository;
    @Mock CourseAttachmentIdGenerator idGenerator;

    private CourseAttachmentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCourseAttachmentApplicationService(
            attachmentRepository, courseRepository, instructorRepository, fileRepository,
            idGenerator, new AuthorizationPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 다른_기관_강사는_첨부할_수_없다() throws Exception {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));

        ApiException forbidden = catchThrowableOfType(
            () -> service.attach(
                new AttachCourseAttachmentCommand(COURSE_ID, FILE_ID),
                otherInstitutionInstructor()),
            ApiException.class);

        assertThat(forbidden.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
    }

    @Test
    void 같은_기관_메인_강사는_첨부한다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, USER_ID, CourseInstructorRole.MAIN)).thenReturn(true);
        when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile()));
        when(idGenerator.generate()).thenReturn(ATTACHMENT_ID);

        CourseAttachment attached = service.attach(
            new AttachCourseAttachmentCommand(COURSE_ID, FILE_ID), sameInstitutionInstructor());

        assertThat(attached.id()).isEqualTo(ATTACHMENT_ID);
        assertThat(attached.fileName()).isEqualTo("강의계획서.pdf");
        assertThat(attached.byteSize()).isEqualTo(251_904);
    }

    @Test
    void 다른_목적으로_업로드된_파일은_붙일_수_없다() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, USER_ID, CourseInstructorRole.MAIN)).thenReturn(true);
        when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(
            new StoredFile(FILE_ID, StoredFilePurpose.PROFILE, "avatar.png",
                "image/png", 1024, USER_ID + "/avatar/" + FILE_ID + ".png", USER_ID, NOW)));

        ApiException invalid = catchThrowableOfType(
            () -> service.attach(
                new AttachCourseAttachmentCommand(COURSE_ID, FILE_ID), sameInstitutionInstructor()),
            ApiException.class);

        assertThat(invalid.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 이미_붙은_파일은_409() {
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile()));
        when(idGenerator.generate()).thenReturn(ATTACHMENT_ID);
        doThrow(new DataIntegrityViolationException("duplicate"))
            .when(attachmentRepository).save(any());

        ApiException conflict = catchThrowableOfType(
            () -> service.attach(
                new AttachCourseAttachmentCommand(COURSE_ID, FILE_ID), platformAdmin()),
            ApiException.class);

        assertThat(conflict.code()).isEqualTo(ApiErrorCode.COURSE_ATTACHMENT_CONFLICT);
    }

    @Test
    void 다른_기관_강사는_삭제할_수_없다() {
        CourseAttachment existing = new CourseAttachment(
            ATTACHMENT_ID, COURSE_ID, FILE_ID, "강의계획서.pdf", 251_904, 0, NOW);
        when(attachmentRepository.findActiveById(ATTACHMENT_ID))
            .thenReturn(Optional.of(existing));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));

        ApiException forbidden = catchThrowableOfType(
            () -> service.remove(COURSE_ID, ATTACHMENT_ID, otherInstitutionInstructor()),
            ApiException.class);

        assertThat(forbidden.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
    }

    @Test
    void 다른_강좌의_첨부는_찾을_수_없다() {
        UUID otherCourseId = UUID.randomUUID();
        CourseAttachment existing = new CourseAttachment(
            ATTACHMENT_ID, otherCourseId, FILE_ID, "강의계획서.pdf", 251_904, 0, NOW);
        when(attachmentRepository.findActiveById(ATTACHMENT_ID))
            .thenReturn(Optional.of(existing));

        ApiException missing = catchThrowableOfType(
            () -> service.remove(COURSE_ID, ATTACHMENT_ID, platformAdmin()),
            ApiException.class);

        assertThat(missing.code()).isEqualTo(ApiErrorCode.COURSE_ATTACHMENT_NOT_FOUND);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "COURSE-001", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, 10, null, null, false, null, CourseStatus.DRAFT, NOW, NOW, null);
    }

    private StoredFile storedFile() {
        return new StoredFile(FILE_ID, StoredFilePurpose.COURSE_ATTACHMENT, "강의계획서.pdf",
            "application/pdf", 251_904, USER_ID + "/doc/" + FILE_ID + ".pdf", USER_ID, NOW);
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }

    private AuthenticatedUserContext sameInstitutionInstructor() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext otherInstitutionInstructor() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", OTHER_INSTITUTION_ID)));
    }
}
