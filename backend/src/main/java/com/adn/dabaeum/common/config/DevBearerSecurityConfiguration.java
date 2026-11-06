package com.adn.dabaeum.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 고정 Bearer 토큰으로 PLATFORM_ADMIN 을 주는 개발 편의 기능. 인터넷에 열린 dev 서버에서 켜지지
 * 않도록 local 프로필에만 둔다. dev 의 관리자 작업은 로그인 JWT 로 한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableConfigurationProperties(DevBearerTokenProperties.class)
public class DevBearerSecurityConfiguration {

    @Bean
    DevBearerAuthenticationFilter devBearerAuthenticationFilter(
        DevBearerTokenProperties properties
    ) {
        return new DevBearerAuthenticationFilter(properties);
    }
}
