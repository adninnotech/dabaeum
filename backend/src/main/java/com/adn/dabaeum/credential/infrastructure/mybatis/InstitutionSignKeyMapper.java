package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstitutionSignKeyMapper {

    int insert(InstitutionSignKeyRow row);

    InstitutionSignKeyRow selectById(@Param("id") UUID id);

    InstitutionSignKeyRow selectCurrent(
        @Param("institutionId") UUID institutionId,
        @Param("purpose") String purpose
    );

    List<InstitutionSignKeyRow> selectByInstitutionId(@Param("institutionId") UUID institutionId);

    int update(@Param("row") InstitutionSignKeyRow row, @Param("expectedVersionNo") int expectedVersionNo);
}
