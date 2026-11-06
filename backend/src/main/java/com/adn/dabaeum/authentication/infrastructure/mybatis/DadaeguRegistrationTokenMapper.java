package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DadaeguRegistrationTokenMapper {

    int insert(DadaeguRegistrationTokenRow row);

    DadaeguRegistrationTokenRow consume(
        @Param("tokenHash") String tokenHash,
        @Param("now") Instant now
    );

    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
