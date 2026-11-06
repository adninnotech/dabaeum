package com.adn.dabaeum.course.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseAttachment;
import com.adn.dabaeum.course.domain.CourseAttachmentRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCourseAttachmentApplicationService
    implements CourseAttachmentApplicationService {

    private final CourseAttachmentRepository attachmentRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository instructorRepository;
    private final StoredFileRepository fileRepository;
    private final CourseAttachmentIdGenerator idGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultCourseAttachmentApplicationService(
        CourseAttachmentRepository attachmentRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        StoredFileRepository fileRepository,
        CourseAttachmentIdGenerator idGenerator,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.attachmentRepository = Objects.requireNonNull(attachmentRepository);
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.instructorRepository = Objects.requireNonNull(instructorRepository);
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseAttachment> list(UUID courseId) {
        findCourse(courseId);
        return attachmentRepository.findActiveByCourseId(courseId);
    }

    @Override
    @Transactional
    public CourseAttachment attach(
        AttachCourseAttachmentCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.courseId() == null || command.fileId() == null) {
            throw validation("fileId");
        }
        Course course = findCourse(command.courseId());
        requireManager(context, course);
        StoredFile file = fileRepository.findById(command.fileId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.FILE_NOT_FOUND, "File not found"));
        if (file.purpose() != StoredFilePurpose.COURSE_ATTACHMENT) {
            throw validation("fileId");
        }

        Instant now = clock.instant();
        CourseAttachment attachment = new CourseAttachment(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            course.id(), file.id(), file.originalName(), file.size(), 0, now);
        try {
            attachmentRepository.save(attachment);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return attachment;
    }

    @Override
    @Transactional
    public CourseAttachment remove(UUID courseId, UUID attachmentId, AuthenticatedUserContext context) {
        if (courseId == null || attachmentId == null) {
            throw validation("attachmentId");
        }
        CourseAttachment existing = attachmentRepository.findActiveById(attachmentId)
            .filter(attachment -> attachment.courseId().equals(courseId))
            .orElseThrow(this::notFound);
        Course course = findCourse(courseId);
        requireManager(context, course);
        if (!attachmentRepository.softDelete(existing.id(), clock.instant())) {
            throw notFound();
        }
        return existing;
    }

    private void requireManager(AuthenticatedUserContext context, Course course) {
        boolean mainAssigned = context != null
            && context.roles().stream().anyMatch(role ->
                "INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), course.institutionId()))
            && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireCourseSessionManager(
            context, course.institutionId(), mainAssigned);
    }

    private Course findCourse(UUID courseId) {
        if (courseId == null) {
            throw validation("courseId");
        }
        return courseRepository.findActiveById(courseId).orElseThrow(this::courseNotFound);
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }

    private ApiException courseNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND, "Course not found");
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_ATTACHMENT_NOT_FOUND,
            "Course attachment not found");
    }

    private ApiException conflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.COURSE_ATTACHMENT_CONFLICT,
            "Course attachment already exists");
    }
}
