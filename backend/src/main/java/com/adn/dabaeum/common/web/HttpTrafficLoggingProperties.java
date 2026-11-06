package com.adn.dabaeum.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * HTTP 교환 로그 정책.
 *
 * <p>{@code bodyEnabled} 가 꺼지면 본문과 쿼리값을 빼고 요청 ID·상태·경로·소요시간만 남긴다.
 * {@code maskSensitive} 는 로컬 환경에서 디버깅을 위해
 * 끌 수 있으며, 끄면 비밀번호·토큰 필드도 그대로 기록되므로 개발 전용으로만 쓴다.
 * {@code prettyJson} 을 켜면 요약 한 줄 뒤에 본문 JSON 을 들여쓴 여러 줄로 남긴다(사람이 읽는 개발 로그용).
 * 요청 하나가 여러 줄이 되므로 줄 단위로 수집·검색하는 환경에서는 끈다.
 */
@ConfigurationProperties(prefix = "dabaeum.http-logging")
public record HttpTrafficLoggingProperties(
    @DefaultValue("true") boolean bodyEnabled,
    @DefaultValue("true") boolean maskSensitive,
    @DefaultValue("false") boolean prettyJson
) {
}
