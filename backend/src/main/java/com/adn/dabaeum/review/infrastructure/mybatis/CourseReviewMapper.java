package com.adn.dabaeum.review.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseReviewMapper {

    int insert(CourseReviewRow row);

    boolean existsByUserIdAndCourseId(
        @Param("userId") UUID userId,
        @Param("courseId") UUID courseId
    );

    boolean hasConfirmedCompletion(
        @Param("userId") UUID userId,
        @Param("courseId") UUID courseId
    );

    List<CourseReviewViewRow> selectByUserId(
        @Param("userId") UUID userId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByUserId(@Param("userId") UUID userId);

    List<CourseReviewViewRow> selectByCourseId(
        @Param("courseId") UUID courseId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByCourseId(@Param("courseId") UUID courseId);
}
