package com.adn.dabaeum.institution.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstitutionJoinApplicationMapper {

    int insert(InstitutionJoinApplicationRow row);

    InstitutionJoinApplicationRow selectById(@Param("id") UUID id);

    InstitutionJoinApplicationRow selectByIdForUpdate(@Param("id") UUID id);

    List<InstitutionJoinApplicationRow> selectPage(
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long count(@Param("status") String status);

    int updateDecision(
        @Param("row") InstitutionJoinApplicationRow row,
        @Param("expectedStatus") String expectedStatus
    );
}
