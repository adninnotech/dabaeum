package com.adn.dabaeum.view.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseViewMapper {

    boolean isPublicCourse(@Param("courseId") UUID courseId);

    int insertIfAbsent(
        @Param("courseId") UUID courseId,
        @Param("viewerKey") String viewerKey,
        @Param("viewDate") LocalDate viewDate,
        @Param("userId") UUID userId,
        @Param("at") Instant at
    );

    int deleteViewsBefore(@Param("date") LocalDate date);

    int insertSaltIfAbsent(@Param("date") LocalDate date, @Param("salt") byte[] salt);

    ViewSaltRow selectSalt(@Param("date") LocalDate date);

    int deleteSaltsBefore(@Param("date") LocalDate date);
}
