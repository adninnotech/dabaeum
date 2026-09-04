package com.adn.dabaeum.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpTrafficLoggingProperties.class)
@Profile({"local", "dev"})
public class HttpTrafficLoggingConfiguration {

    @Bean
    HttpTrafficLoggingFilter httpTrafficLoggingFilter(
        ObjectMapper objectMapper,
        HttpTrafficLoggingProperties properties
    ) {
        return new HttpTrafficLoggingFilter(objectMapper, properties);
    }
}
