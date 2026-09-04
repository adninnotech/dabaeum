package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EnrollmentQueryMapper {

    List<MyEnrollmentViewRow> selectMyEnrollments(
        @Param("userId") UUID userId,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countMyEnrollments(
        @Param("userId") UUID userId,
        @Param("status") String status
    );

    List<InstitutionEnrollmentViewRow> selectInstitutionEnrollments(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countInstitutionEnrollments(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status
    );

    LearningSummaryRow selectLearningSummary(@Param("userId") UUID userId);

    List<LearningCourseViewRow> selectLearningCourses(
        @Param("userId") UUID userId,
        @Param("learningStatus") String learningStatus,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countLearningCourses(
        @Param("userId") UUID userId,
        @Param("learningStatus") String learningStatus
    );

    List<EnrollmentProgressViewRow> selectInstructorEnrollmentProgress(
        @Param("instructorUserId") UUID instructorUserId,
        @Param("courseId") UUID courseId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countInstructorEnrollmentProgress(
        @Param("instructorUserId") UUID instructorUserId,
        @Param("courseId") UUID courseId
    );

    EnrollmentProgressViewRow selectEnrollmentProgress(
        @Param("enrollmentId") UUID enrollmentId
    );
}
