package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.util.UUID;

public interface EnrollmentQueryService {

    MyEnrollmentPage listMyEnrollments(
        AuthenticatedUserContext actor, EnrollmentStatus status,
        int page, int size, String sort);

    InstitutionEnrollmentPage listInstitutionEnrollments(
        AuthenticatedUserContext actor, UUID institutionId, EnrollmentStatus status,
        int page, int size, String sort);

    com.adn.dabaeum.enrollment.domain.LearningSummary learningSummary(
        AuthenticatedUserContext actor);

    LearningCoursePage listLearningCourses(
        AuthenticatedUserContext actor, String learningStatus, int page, int size);

    EnrollmentProgressPage listInstructorEnrollmentProgress(
        AuthenticatedUserContext actor, UUID courseId, int page, int size);

    com.adn.dabaeum.enrollment.domain.EnrollmentProgressView enrollmentProgress(
        AuthenticatedUserContext actor, UUID enrollmentId);
}
