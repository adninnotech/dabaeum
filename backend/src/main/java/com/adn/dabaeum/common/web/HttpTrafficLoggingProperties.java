package com.adn.dabaeum.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * HTTP 교환 로그 정책.
 *
 * <p>{@code bodyEnabled} 가 꺼지면 상태·경로·소요시간만 남긴다. 운영에서는 본문에 개인정보가
 * 섞이므로 끄는 것이 기본 방침이다. {@code maskSensitive} 는 개발 환경에서 디버깅을 위해
 * 끌 수 있으며, 끄면 비밀번호·토큰 필드도 그대로 기록되므로 개발 전용으로만 쓴다.
 */
@ConfigurationProperties(prefix = "dabaeum.http-logging")
public record HttpTrafficLoggingProperties(
    @DefaultValue("true") boolean bodyEnabled,
    @DefaultValue("true") boolean maskSensitive
) {
}
