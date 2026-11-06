package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
import com.adn.dabaeum.course.domain.CourseStatus;
import java.time.Instant;
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

    long countActiveFiltered(
        @Param("keyword") String keyword,
        @Param("educationType") CourseEducationType educationType,
        @Param("selectionMethod") CourseSelectionMethod selectionMethod,
        @Param("status") CourseStatus status,
        @Param("includeAllDraft") boolean includeAllDraft,
        @Param("draftVisibleInstitutionIds") List<UUID> draftVisibleInstitutionIds,
        @Param("classificationPrefix") String classificationPrefix
    );

    int updateActive(CourseRow row);

    int softDeleteDraft(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    CourseViewRow selectActiveViewById(@Param("id") UUID id);

    List<CourseViewRow> selectActiveViewsByIds(@Param("ids") List<UUID> ids);

    List<CourseViewRow> selectActiveViewPage(
        @Param("criteria") CoursePageCriteria criteria
    );
}
