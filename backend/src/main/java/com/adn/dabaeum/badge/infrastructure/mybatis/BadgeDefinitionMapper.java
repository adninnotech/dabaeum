package com.adn.dabaeum.badge.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BadgeDefinitionMapper {

    int insert(BadgeDefinitionRow row);

    BadgeDefinitionRow selectById(@Param("id") UUID id);

    List<BadgeDefinitionRow> selectAll(@Param("activeOnly") boolean activeOnly);

    int update(BadgeDefinitionRow row);

    int delete(@Param("id") UUID id);

    boolean existsIssued(@Param("id") UUID id);
}
