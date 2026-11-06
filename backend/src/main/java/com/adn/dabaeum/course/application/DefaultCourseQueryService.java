package com.adn.dabaeum.course.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.user.domain.UserRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultCourseQueryService implements CourseQueryService {

    private final CourseQueryRepository queryRepository;
    private final AuthorizationPolicy authorizationPolicy;
    private final UserRepository userRepository;

    public DefaultCourseQueryService(
        CourseQueryRepository queryRepository,
        AuthorizationPolicy authorizationPolicy,
        UserRepository userRepository
    ) {
        this.queryRepository = queryRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCoursePage listInstructorCourses(
        AuthenticatedUserContext actor, CourseStatus status,
        int page, int size, String sort
    ) {
        // 본문은 ...Of 한 곳에만 둔다. 자기 호출이라 트랜잭션 프록시를 안 타지만
        // 두 메서드의 속성이 (readOnly = true) 로 같아서 문제가 없다.
        Objects.requireNonNull(actor, "actor");
        return listInstructorCoursesOf(actor.userId(), status, page, size, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCoursePage listInstructorCoursesOf(
        UUID instructorUserId, CourseStatus status, int page, int size, String sort
    ) {
        requireUser(instructorUserId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstructorCourses(
            instructorUserId, status, size, offset, sort);
        long totalElements = queryRepository.countInstructorCourses(
            instructorUserId, status);
        return new InstructorCoursePage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCourseStats instructorCourseStats(AuthenticatedUserContext actor) {
        Objects.requireNonNull(actor, "actor");
        return instructorCourseStatsOf(actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCourseStats instructorCourseStatsOf(UUID instructorUserId) {
        requireUser(instructorUserId);
        return queryRepository.instructorCourseStats(instructorUserId);
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
        AuthenticatedUserContext actor, UUID institutionId, String status, String keyword,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        authorizationPolicy.requireCourseManager(actor, institutionId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstitutionInstructors(
            institutionId, status, keyword, size, offset, sort);
        long totalElements = queryRepository.countInstitutionInstructors(
            institutionId, status, keyword);
        return new InstitutionInstructorPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    /** 없는 사용자에게는 빈 페이지가 아니라 404 를 준다 — 관리자 화면이 오탈자를 알아채야 한다. */
    private void requireUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        if (userRepository.findById(userId).isEmpty()) {
            throw new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "User not found");
        }
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
