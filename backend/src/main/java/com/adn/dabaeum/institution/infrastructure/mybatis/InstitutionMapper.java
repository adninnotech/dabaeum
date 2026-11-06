package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstitutionMapper {

    int insert(InstitutionRow row);

    InstitutionRow selectById(@Param("id") UUID id);

    List<InstitutionRow> selectByIds(@Param("ids") List<UUID> ids);

    InstitutionRow selectActiveById(@Param("id") UUID id);

    InstitutionRow selectActiveByCode(
        @Param("institutionCode") String institutionCode
    );

    List<InstitutionListRow> selectActivePage(
        @Param("criteria") InstitutionPageCriteria criteria
    );

    long countActiveFiltered(
        @Param("keyword") String keyword,
        @Param("status") InstitutionStatus status,
        @Param("region") String region
    );

    int updateActive(InstitutionRow row);
}
