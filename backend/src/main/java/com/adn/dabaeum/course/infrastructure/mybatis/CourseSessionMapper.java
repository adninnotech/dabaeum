package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseSessionPageCriteria;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseSessionMapper {

    int insert(CourseSessionRow row);

    CourseSessionRow selectById(@Param("id") UUID id);

    CourseSessionRow selectByIdForUpdate(@Param("id") UUID id);

    List<CourseSessionRow> selectAttendanceEligibleByCourseId(
        @Param("courseId") UUID courseId,
        @Param("evaluatedAt") Instant evaluatedAt
    );

    boolean existsAttendanceIneligibleByCourseId(
        @Param("courseId") UUID courseId,
        @Param("evaluatedAt") Instant evaluatedAt
    );

    CourseSessionRow selectByCourseIdAndSessionNo(
        @Param("courseId") UUID courseId,
        @Param("sessionNo") int sessionNo
    );

    List<CourseSessionRow> selectByCourseId(@Param("courseId") UUID courseId);

    List<CourseSessionRow> selectPage(
        @Param("criteria") CourseSessionPageCriteria criteria
    );

    long countByCourseId(@Param("courseId") UUID courseId);

    int update(CourseSessionRow row);

    int reopen(
        @Param("id") UUID id,
        @Param("from") String from,
        @Param("to") String to,
        @Param("updatedAt") Instant updatedAt
    );

    boolean existsSessionDependenciesByCourseId(@Param("courseId") UUID courseId);

    boolean existsNonScheduledByCourseId(@Param("courseId") UUID courseId);

    Instant selectFirstActiveStartByCourseId(@Param("courseId") UUID courseId);

    int deleteAllByCourseId(@Param("courseId") UUID courseId);

    int shiftSessionNoByOffset(@Param("courseId") UUID courseId, @Param("offset") int offset);

    int updateSessionNo(@Param("id") UUID id, @Param("sessionNo") int sessionNo);
}
