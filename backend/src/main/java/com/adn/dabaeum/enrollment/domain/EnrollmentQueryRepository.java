package com.adn.dabaeum.enrollment.domain;

import java.util.List;
import java.util.UUID;

public interface EnrollmentQueryRepository {

    List<MyEnrollmentView> findMyEnrollments(
        UUID userId, EnrollmentStatus status, int limit, int offset, String sort);

    long countMyEnrollments(UUID userId, EnrollmentStatus status);

    List<InstitutionEnrollmentView> findInstitutionEnrollments(
        UUID institutionId, EnrollmentStatus status, int limit, int offset, String sort);

    long countInstitutionEnrollments(UUID institutionId, EnrollmentStatus status);

    /**
     * 수강현황관리 화면의 사이트 전체 수강신청 목록. findInstitutionEnrollments 와 같은
     * SELECT·조인을 기관 조건 없이 쓴다. keyword 는 과정명·수강자명에 매칭한다.
     */
    List<InstitutionEnrollmentView> findAllEnrollments(
        EnrollmentStatus status, UUID institutionId, UUID courseId,
        com.adn.dabaeum.course.domain.CourseEducationType educationType, String keyword,
        int limit, int offset, String sort);

    long countAllEnrollments(
        EnrollmentStatus status, UUID institutionId, UUID courseId,
        com.adn.dabaeum.course.domain.CourseEducationType educationType, String keyword);

    LearningSummary learningSummary(UUID userId);

    /** B-4 학습 통계의 원장 집계(학습 시간·평균 진도율·문의 건수). */
    LearningActivity learningActivity(UUID userId);

    List<LearningCourseView> findLearningCourses(
        UUID userId, String learningStatus, String keyword, int limit, int offset);

    long countLearningCourses(UUID userId, String learningStatus, String keyword);

    List<EnrollmentProgressView> findInstructorEnrollmentProgress(
        UUID instructorUserId, UUID courseId, int limit, int offset);

    long countInstructorEnrollmentProgress(UUID instructorUserId, UUID courseId);

    java.util.Optional<EnrollmentProgressView> findEnrollmentProgress(UUID enrollmentId);
}
