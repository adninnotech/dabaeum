package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultInquiryApplicationService implements InquiryApplicationService {

    private final InquiryRepository inquiryRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final AuthorizationPolicy authorizationPolicy;

    public DefaultInquiryApplicationService(
        InquiryRepository inquiryRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository courseInstructorRepository,
        AuthorizationPolicy authorizationPolicy
    ) {
        this.inquiryRepository = inquiryRepository;
        this.courseRepository = courseRepository;
        this.courseInstructorRepository = courseInstructorRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional
    public Inquiry create(CreateInquiryCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.courseId() != null) {
            courseRepository.findActiveById(command.courseId())
                .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND,
                    "Course not found"));
        }
        Inquiry inquiry = new Inquiry(
            UUID.randomUUID(), command.actor().userId(), command.courseId(),
            command.title(), command.content(), InquiryStatus.PENDING,
            null, null, null, command.requestedAt(), command.requestedAt());
        inquiryRepository.save(inquiry);
        return inquiry;
    }

    @Override
    @Transactional(readOnly = true)
    public Inquiry get(UUID inquiryId, AuthenticatedUserContext actor) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
            .orElseThrow(() -> notFound(ApiErrorCode.INQUIRY_NOT_FOUND,
                "Inquiry not found"));
        if (Objects.equals(actor.userId(), inquiry.userId())) {
            return inquiry;
        }
        requireAnswerAuthority(actor, inquiry);
        return inquiry;
    }

    @Override
    @Transactional
    public Inquiry reply(ReplyInquiryCommand command) {
        Objects.requireNonNull(command, "command");
        Inquiry inquiry = inquiryRepository.findById(command.inquiryId())
            .orElseThrow(() -> notFound(ApiErrorCode.INQUIRY_NOT_FOUND,
                "Inquiry not found"));
        requireAnswerAuthority(command.actor(), inquiry);
        if (inquiry.status() == InquiryStatus.ANSWERED) {
            throw new ApiException(
                HttpStatus.CONFLICT, ApiErrorCode.INQUIRY_STATUS_CONFLICT,
                "Inquiry is already answered");
        }
        Inquiry answered = new Inquiry(
            inquiry.id(), inquiry.userId(), inquiry.courseId(),
            inquiry.title(), inquiry.content(), InquiryStatus.ANSWERED,
            command.content(), command.actor().userId(), command.requestedAt(),
            inquiry.createdAt(), command.requestedAt());
        if (!inquiryRepository.updateAnswer(answered)) {
            throw new ApiException(
                HttpStatus.CONFLICT, ApiErrorCode.INQUIRY_STATUS_CONFLICT,
                "Inquiry was answered concurrently");
        }
        return answered;
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listMine(
        AuthenticatedUserContext actor, InquiryStatus status, int page, int size
    ) {
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findByUserId(actor.userId(), status, size, offset);
        long totalElements = inquiryRepository.countByUserId(actor.userId(), status);
        return new InquiryViewPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listForInstructor(
        AuthenticatedUserContext actor, InquiryStatus status, UUID courseId,
        int page, int size
    ) {
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findByInstructorUserId(
            actor.userId(), status, courseId, size, offset);
        long totalElements = inquiryRepository.countByInstructorUserId(
            actor.userId(), status, courseId);
        return new InquiryViewPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listForInstitution(
        AuthenticatedUserContext actor, UUID institutionId, InquiryStatus status,
        UUID courseId, int page, int size
    ) {
        authorizationPolicy.requireCourseManager(actor, institutionId);
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findByInstitutionId(
            institutionId, status, courseId, size, offset);
        long totalElements = inquiryRepository.countByInstitutionId(
            institutionId, status, courseId);
        return new InquiryViewPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    private void requireAnswerAuthority(AuthenticatedUserContext actor, Inquiry inquiry) {
        if (inquiry.courseId() == null) {
            authorizationPolicy.requirePlatformAdmin(actor);
            return;
        }
        Course course = courseRepository.findActiveById(inquiry.courseId())
            .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND,
                "Course not found"));
        boolean instructorAssigned = courseInstructorRepository
            .existsByCourseIdAndUserId(course.id(), actor.userId());
        authorizationPolicy.requireCourseSessionManager(
            actor, course.institutionId(), instructorAssigned);
    }

    private ApiException notFound(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
