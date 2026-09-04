package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseInstructor;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseInstructorMapper {

    void insert(CourseInstructor instructor);

    CourseInstructorRow selectByCourseIdAndUserId(
        @Param("courseId") UUID courseId,
        @Param("userId") UUID userId
    );

    List<CourseInstructorRow> selectByCourseId(@Param("courseId") UUID courseId);

    boolean existsByCourseIdAndUserId(
        @Param("courseId") UUID courseId,
        @Param("userId") UUID userId
    );

    boolean existsByCourseIdAndUserIdAndRole(
        @Param("courseId") UUID courseId,
        @Param("userId") UUID userId,
        @Param("role") CourseInstructorRole role
    );

    boolean existsByUserIdAndInstitutionId(
        @Param("userId") UUID userId,
        @Param("institutionId") UUID institutionId
    );

    int updateRole(
        @Param("instructor") CourseInstructor instructor,
        @Param("expectedRole") CourseInstructorRole expectedRole
    );

    int deleteByCourseIdAndUserId(
        @Param("courseId") UUID courseId,
        @Param("userId") UUID userId
    );
}
