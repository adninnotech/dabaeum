package com.adn.dabaeum.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    /** 모르는 필드와 정수 칸의 소수(1.5 를 1 로 버리던 기본값)는 400 으로 거부한다. */
    @Bean
    JsonMapperBuilderCustomizer strictJsonProperties() {
        return builder -> builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
