package com.adn.dabaeum.completion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CompletionProperties.class)
public class CompletionConfiguration {
}
