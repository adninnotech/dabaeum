package com.adn.dabaeum.file.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileAccessMapper {

    boolean publiclyLinked(@Param("fileId") UUID fileId,
        @Param("purpose") String purpose, @Param("now") Instant now);

    boolean linked(@Param("fileId") UUID fileId, @Param("purpose") String purpose);

    boolean managedByInstitution(@Param("fileId") UUID fileId,
        @Param("purpose") String purpose, @Param("institutionId") UUID institutionId);
}
