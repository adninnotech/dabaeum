package com.adn.dabaeum.file.infrastructure.mybatis;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StoredFileMapper {

    int insert(StoredFileRow row);

    StoredFileRow selectById(@Param("id") UUID id);
}
