package com.adn.dabaeum.course.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseAttachmentMapper {

    int insert(CourseAttachmentRow row);

    List<CourseAttachmentRow> selectActiveByCourseId(@Param("courseId") UUID courseId);

    CourseAttachmentRow selectActiveById(@Param("id") UUID id);

    int softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);
}
