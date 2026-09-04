package com.adn.dabaeum.course.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultCourseQueryService implements CourseQueryService {

    private final CourseQueryRepository queryRepository;
    private final AuthorizationPolicy authorizationPolicy;

    public DefaultCourseQueryService(
        CourseQueryRepository queryRepository,
        AuthorizationPolicy authorizationPolicy
    ) {
        this.queryRepository = queryRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCoursePage listInstructorCourses(
        AuthenticatedUserContext actor, CourseStatus status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstructorCourses(
            actor.userId(), status, size, offset, sort);
        long totalElements = queryRepository.countInstructorCourses(
            actor.userId(), status);
        return new InstructorCoursePage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCourseStats instructorCourseStats(AuthenticatedUserContext actor) {
        Objects.requireNonNull(actor, "actor");
        return queryRepository.instructorCourseStats(actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public CoursePage listInstitutionCourses(
        AuthenticatedUserContext actor, UUID institutionId, CourseStatus status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        authorizationPolicy.requireCourseManager(actor, institutionId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstitutionCourses(
            institutionId, status, size, offset, sort);
        long totalElements = queryRepository.countInstitutionCourses(
            institutionId, status);
        return new CoursePage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionInstructorPage listInstitutionInstructors(
        AuthenticatedUserContext actor, UUID institutionId, String status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        authorizationPolicy.requireCourseManager(actor, institutionId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstitutionInstructors(
            institutionId, status, size, offset, sort);
        long totalElements = queryRepository.countInstitutionInstructors(
            institutionId, status);
        return new InstitutionInstructorPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
