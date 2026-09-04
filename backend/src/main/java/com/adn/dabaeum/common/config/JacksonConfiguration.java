package com.adn.dabaeum.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer strictJsonProperties() {
        return builder -> builder.enable(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
        );
    }
}
