package com.adn.dabaeum.completion.infrastructure.mybatis;

import com.adn.dabaeum.completion.domain.CompletionConfirmedEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompletionOutboxMapper {

    int insert(
        @Param("event") CompletionConfirmedEvent event,
        @Param("payloadJson") String payloadJson
    );
}
