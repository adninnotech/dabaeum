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

    LearningSummary learningSummary(UUID userId);

    List<LearningCourseView> findLearningCourses(
        UUID userId, String learningStatus, int limit, int offset);

    long countLearningCourses(UUID userId, String learningStatus);

    List<EnrollmentProgressView> findInstructorEnrollmentProgress(
        UUID instructorUserId, UUID courseId, int limit, int offset);

    long countInstructorEnrollmentProgress(UUID instructorUserId, UUID courseId);

    java.util.Optional<EnrollmentProgressView> findEnrollmentProgress(UUID enrollmentId);
}
