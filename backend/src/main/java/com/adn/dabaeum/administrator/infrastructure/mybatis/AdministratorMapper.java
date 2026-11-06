package com.adn.dabaeum.administrator.infrastructure.mybatis;

import com.adn.dabaeum.administrator.domain.AdministratorCriteria;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdministratorMapper {

    List<AdministratorRow> search(@Param("criteria") AdministratorCriteria criteria);

    long count(@Param("criteria") AdministratorCriteria criteria);

    AdministratorRow selectById(@Param("userId") UUID userId, @Param("now") Instant now);
}
