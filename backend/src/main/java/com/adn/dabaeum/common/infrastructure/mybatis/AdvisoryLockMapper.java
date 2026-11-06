package com.adn.dabaeum.common.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdvisoryLockMapper {

    boolean tryXactLock(@Param("key") long key);
}
