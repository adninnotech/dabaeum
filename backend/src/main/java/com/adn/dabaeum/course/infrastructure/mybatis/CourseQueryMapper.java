package com.adn.dabaeum.course.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseQueryMapper {

    List<InstructorCourseViewRow> selectInstructorCourses(
        @Param("instructorUserId") UUID instructorUserId,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countInstructorCourses(
        @Param("instructorUserId") UUID instructorUserId,
        @Param("status") String status
    );

    InstructorCourseStatsRow selectInstructorCourseStats(
        @Param("instructorUserId") UUID instructorUserId
    );

    List<CourseRow> selectInstitutionCourses(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countInstitutionCourses(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status
    );

    List<InstitutionInstructorViewRow> selectInstitutionInstructors(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countInstitutionInstructors(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status
    );
}
