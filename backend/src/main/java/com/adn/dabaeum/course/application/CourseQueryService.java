package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import java.util.UUID;

public interface CourseQueryService {

    InstructorCoursePage listInstructorCourses(
        AuthenticatedUserContext actor, CourseStatus status,
        int page, int size, String sort);

    /**
     * userId 를 그대로 받는 일반화 버전. B-4 관리자 화면이 남의 배정 과정을 보는 데 쓴다.
     * 인가는 하지 않는다 — 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표가 정한다.
     */
    InstructorCoursePage listInstructorCoursesOf(
        UUID instructorUserId, CourseStatus status, int page, int size, String sort);

    InstructorCourseStats instructorCourseStats(AuthenticatedUserContext actor);

    InstructorCourseStats instructorCourseStatsOf(UUID instructorUserId);

    CoursePage listInstitutionCourses(
        AuthenticatedUserContext actor, UUID institutionId, CourseStatus status,
        int page, int size, String sort);

    InstitutionInstructorPage listInstitutionInstructors(
        AuthenticatedUserContext actor, UUID institutionId, String status, String keyword,
        int page, int size, String sort);
}
