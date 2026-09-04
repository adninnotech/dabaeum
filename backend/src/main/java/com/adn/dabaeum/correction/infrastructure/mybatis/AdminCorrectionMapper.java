package com.adn.dabaeum.correction.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminCorrectionMapper {

    int insert(AdminCorrectionRow row);
}
