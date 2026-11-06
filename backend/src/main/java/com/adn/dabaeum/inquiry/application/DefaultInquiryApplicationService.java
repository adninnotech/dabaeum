package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.user.domain.UserRepository;
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
    private final UserRepository userRepository;

    public DefaultInquiryApplicationService(
        InquiryRepository inquiryRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository courseInstructorRepository,
        AuthorizationPolicy authorizationPolicy,
        UserRepository userRepository
    ) {
        this.inquiryRepository = inquiryRepository;
        this.courseRepository = courseRepository;
        this.courseInstructorRepository = courseInstructorRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.userRepository = userRepository;
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
            null, null, null, command.requestedAt(), command.requestedAt(),
            command.type());
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
            inquiry.createdAt(), command.requestedAt(), inquiry.type());
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
        AuthenticatedUserContext actor, InquiryStatus status, String keyword,
        int page, int size
    ) {
        // 본문은 listOf 한 곳에만 둔다. 자기 호출이라 트랜잭션 프록시를 안 타지만
        // 두 메서드의 속성이 (readOnly = true) 로 같아서 문제가 없다.
        Objects.requireNonNull(actor, "actor");
        return listOf(actor.userId(), status, keyword, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listOf(
        UUID userId, InquiryStatus status, String keyword, int page, int size
    ) {
        requireUser(userId);
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findByUserId(
            userId, status, keyword, size, offset);
        long totalElements = inquiryRepository.countByUserId(
            userId, status, keyword);
        return new InquiryViewPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listForInstructor(
        AuthenticatedUserContext actor, InquiryStatus status, UUID courseId,
        int page, int size
    ) {
        // 본문은 listForInstructorOf 한 곳에만 둔다. 자기 호출이라 트랜잭션 프록시를 안 타지만
        // 두 메서드의 속성이 (readOnly = true) 로 같아서 문제가 없다.
        Objects.requireNonNull(actor, "actor");
        return listForInstructorOf(actor.userId(), status, courseId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listForInstructorOf(
        UUID userId, InquiryStatus status, UUID courseId, int page, int size
    ) {
        requireUser(userId);
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findByInstructorUserId(
            userId, status, courseId, size, offset);
        long totalElements = inquiryRepository.countByInstructorUserId(
            userId, status, courseId);
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

    @Override
    @Transactional(readOnly = true)
    public CourseInquiryViewPage listForCourse(
        AuthenticatedUserContext actor, UUID courseId, InquiryType type, int page, int size
    ) {
        Objects.requireNonNull(actor, "actor");
        Course course = courseRepository.findActiveById(courseId)
            .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND, "Course not found"));
        // 목록 자체는 학습자에게도 열려 있다(E-2 Q&A). 다만 문의자 실명·연락처를 볼 수 있는지는
        // 역할 이름이 아니라 **이 강좌의 기관** 으로 판정한다. requireAnswerAuthority 와 같은 규칙이다.
        boolean manager = authorizationPolicy.isCourseSessionManager(
            actor, course.institutionId(),
            courseInstructorRepository.existsByCourseIdAndUserId(course.id(), actor.userId()));
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findByCourseId(courseId, type, size, offset);
        long totalElements = inquiryRepository.countByCourseId(courseId, type);
        return new CourseInquiryViewPage(
            new InquiryViewPage(data, page, size, totalElements, totalPages(totalElements, size)),
            manager);
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryViewPage listAll(
        InquiryStatus status, InquiryType inquiryType, CourseEducationType educationType,
        UUID institutionId, UUID courseId, String keyword, int page, int size
    ) {
        int offset = Math.multiplyExact(page, size);
        var data = inquiryRepository.findForAdmin(
            status, inquiryType, educationType, institutionId, courseId, keyword, size, offset);
        long totalElements = inquiryRepository.countForAdmin(
            status, inquiryType, educationType, institutionId, courseId, keyword);
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

    /** 없는 사용자에게는 빈 페이지가 아니라 404 를 준다 — 관리자 화면이 오탈자를 알아채야 한다. */
    private void requireUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        if (userRepository.findById(userId).isEmpty()) {
            throw notFound(ApiErrorCode.USER_NOT_FOUND, "User not found");
        }
    }

    private ApiException notFound(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
