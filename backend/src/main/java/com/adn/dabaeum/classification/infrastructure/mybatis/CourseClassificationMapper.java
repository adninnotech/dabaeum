package com.adn.dabaeum.classification.infrastructure.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseClassificationMapper {

    List<CourseClassificationRow> selectAll();

    int updateEnabled(@Param("code") String code, @Param("enabled") boolean enabled);
}
