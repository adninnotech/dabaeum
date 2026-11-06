package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstitutionSigningKeyRequestMapper {

    int insert(@Param("request") InstitutionSigningKeyRequest request);
}
