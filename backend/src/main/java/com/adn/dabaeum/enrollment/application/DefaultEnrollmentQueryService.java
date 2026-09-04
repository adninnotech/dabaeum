package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultEnrollmentQueryService implements EnrollmentQueryService {

    private final EnrollmentQueryRepository queryRepository;
    private final AuthorizationPolicy authorizationPolicy;

    public DefaultEnrollmentQueryService(
        EnrollmentQueryRepository queryRepository,
        AuthorizationPolicy authorizationPolicy
    ) {
        this.queryRepository = queryRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public MyEnrollmentPage listMyEnrollments(
        AuthenticatedUserContext actor, EnrollmentStatus status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findMyEnrollments(
            actor.userId(), status, size, offset, sort);
        long totalElements = queryRepository.countMyEnrollments(actor.userId(), status);
        return new MyEnrollmentPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionEnrollmentPage listInstitutionEnrollments(
        AuthenticatedUserContext actor, UUID institutionId, EnrollmentStatus status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        authorizationPolicy.requireCourseManager(actor, institutionId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstitutionEnrollments(
            institutionId, status, size, offset, sort);
        long totalElements = queryRepository.countInstitutionEnrollments(
            institutionId, status);
        return new InstitutionEnrollmentPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public com.adn.dabaeum.enrollment.domain.LearningSummary learningSummary(
        AuthenticatedUserContext actor
    ) {
        Objects.requireNonNull(actor, "actor");
        return queryRepository.learningSummary(actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public LearningCoursePage listLearningCourses(
        AuthenticatedUserContext actor, String learningStatus, int page, int size
    ) {
        Objects.requireNonNull(actor, "actor");
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findLearningCourses(
            actor.userId(), learningStatus, size, offset);
        long totalElements = queryRepository.countLearningCourses(
            actor.userId(), learningStatus);
        return new LearningCoursePage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentProgressPage listInstructorEnrollmentProgress(
        AuthenticatedUserContext actor, UUID courseId, int page, int size
    ) {
        Objects.requireNonNull(actor, "actor");
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstructorEnrollmentProgress(
            actor.userId(), courseId, size, offset);
        long totalElements = queryRepository.countInstructorEnrollmentProgress(
            actor.userId(), courseId);
        return new EnrollmentProgressPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public com.adn.dabaeum.enrollment.domain.EnrollmentProgressView enrollmentProgress(
        AuthenticatedUserContext actor, UUID enrollmentId
    ) {
        Objects.requireNonNull(actor, "actor");
        var progress = queryRepository.findEnrollmentProgress(enrollmentId)
            .orElseThrow(() -> new com.adn.dabaeum.common.api.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                com.adn.dabaeum.common.api.ApiErrorCode.ENROLLMENT_NOT_FOUND,
                "Enrollment not found"));
        if (!Objects.equals(actor.userId(), progress.userId())) {
            authorizationPolicy.requirePlatformAdmin(actor);
        }
        return progress;
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
