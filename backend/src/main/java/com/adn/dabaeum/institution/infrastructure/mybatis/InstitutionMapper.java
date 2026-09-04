package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstitutionMapper {

    int insert(InstitutionRow row);

    InstitutionRow selectById(@Param("id") UUID id);

    InstitutionRow selectActiveById(@Param("id") UUID id);

    InstitutionRow selectActiveByCode(
        @Param("institutionCode") String institutionCode
    );

    List<InstitutionRow> selectActivePage(
        @Param("criteria") InstitutionPageCriteria criteria
    );

    long countActive();

    int updateActive(InstitutionRow row);
}
