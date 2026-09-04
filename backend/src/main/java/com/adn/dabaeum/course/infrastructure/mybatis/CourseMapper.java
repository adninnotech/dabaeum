package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CoursePageCriteria;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseMapper {

    int insert(CourseRow row);

    CourseRow selectById(@Param("id") UUID id);

    CourseRow selectActiveById(@Param("id") UUID id);

    CourseRow selectActiveByIdForUpdate(@Param("id") UUID id);

    CourseRow selectActiveByInstitutionAndCode(
        @Param("institutionId") UUID institutionId,
        @Param("courseCode") String courseCode
    );

    List<CourseRow> selectActivePage(
        @Param("criteria") CoursePageCriteria criteria
    );

    long countActive();

    int updateActive(CourseRow row);
}
