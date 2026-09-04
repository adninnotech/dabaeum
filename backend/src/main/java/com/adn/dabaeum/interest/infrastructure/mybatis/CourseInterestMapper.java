package com.adn.dabaeum.interest.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseInterestMapper {

    int insert(CourseInterestRow row);

    CourseInterestRow selectById(@Param("id") UUID id);

    CourseInterestRow selectByUserIdAndCourseId(
        @Param("userId") UUID userId,
        @Param("courseId") UUID courseId
    );

    List<CourseInterestViewRow> selectByUserId(
        @Param("userId") UUID userId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByUserId(@Param("userId") UUID userId);

    int deleteById(@Param("id") UUID id);
}
