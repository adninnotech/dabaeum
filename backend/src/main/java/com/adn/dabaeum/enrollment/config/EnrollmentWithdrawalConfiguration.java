package com.adn.dabaeum.enrollment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EnrollmentWithdrawalProperties.class)
public class EnrollmentWithdrawalConfiguration {
}
