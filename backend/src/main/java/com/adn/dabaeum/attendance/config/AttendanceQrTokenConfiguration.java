package com.adn.dabaeum.attendance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttendanceQrTokenProperties.class)
public class AttendanceQrTokenConfiguration {
}
