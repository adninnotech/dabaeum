package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.util.UUID;

public interface EnrollmentQueryService {

    MyEnrollmentPage listMyEnrollments(
        AuthenticatedUserContext actor, EnrollmentStatus status,
        int page, int size, String sort);

    /**
     * userId 를 그대로 받는 일반화 버전. B-4 관리자 화면이 남의 이력을 보는 데 쓴다.
     * 여기서는 인가를 하지 않는다 — 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표가 정한다.
     */
    MyEnrollmentPage listEnrollmentsOf(
        UUID userId, EnrollmentStatus status, int page, int size, String sort);

    InstitutionEnrollmentPage listInstitutionEnrollments(
        AuthenticatedUserContext actor, UUID institutionId, EnrollmentStatus status,
        int page, int size, String sort);

    /**
     * 수강현황관리 화면의 사이트 전체 수강신청 목록. listInstitutionEnrollments 와 달리
     * 인가를 하지 않는다 — 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표(/api/v1/admin/*
     * 는 PLATFORM_ADMIN 전용)가 정한다.
     */
    InstitutionEnrollmentPage listAllEnrollments(
        EnrollmentStatus status, UUID institutionId, UUID courseId,
        com.adn.dabaeum.course.domain.CourseEducationType educationType, String keyword,
        int page, int size, String sort);

    com.adn.dabaeum.enrollment.domain.LearningSummary learningSummary(
        AuthenticatedUserContext actor);

    com.adn.dabaeum.enrollment.domain.LearningSummary learningSummaryOf(UUID userId);

    /** B-4 회원 상세 학습 통계. 요약 3값에 원장 집계 3값을 더해 돌려준다. */
    com.adn.dabaeum.enrollment.domain.UserLearningStats learningStatsOf(UUID userId);

    LearningCoursePage listLearningCourses(
        AuthenticatedUserContext actor, String learningStatus, String keyword,
        int page, int size);

    LearningCoursePage listLearningCoursesOf(
        UUID userId, String learningStatus, String keyword, int page, int size);

    EnrollmentProgressPage listInstructorEnrollmentProgress(
        AuthenticatedUserContext actor, UUID courseId, int page, int size);

    /**
     * userId 를 그대로 받는 일반화 버전. B-4/관리자 강사 상세 화면이 남의 강사 수강 현황을
     * 보는 데 쓴다. 인가는 하지 않는다 — 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표가
     * 정한다.
     */
    EnrollmentProgressPage listInstructorEnrollmentProgressOf(
        UUID userId, UUID courseId, int page, int size);

    com.adn.dabaeum.enrollment.domain.EnrollmentProgressView enrollmentProgress(
        AuthenticatedUserContext actor, UUID enrollmentId);
}
