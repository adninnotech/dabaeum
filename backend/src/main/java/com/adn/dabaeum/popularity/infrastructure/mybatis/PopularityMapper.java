package com.adn.dabaeum.popularity.infrastructure.mybatis;

import com.adn.dabaeum.popularity.domain.PopularCourseScore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PopularityMapper {

    int deleteWindow(@Param("windowDays") int windowDays);

    int insertWindow(
        @Param("windowDays") int windowDays,
        @Param("enrollmentWeight") int enrollmentWeight,
        @Param("viewWeight") int viewWeight,
        @Param("since") Instant since,
        @Param("sinceDate") LocalDate sinceDate,
        @Param("now") Instant now
    );

    List<PopularCourseScore> selectTop(
        @Param("segmentType") String segmentType,
        @Param("segmentKey") String segmentKey,
        @Param("windowDays") int windowDays,
        @Param("limit") int limit
    );
}
