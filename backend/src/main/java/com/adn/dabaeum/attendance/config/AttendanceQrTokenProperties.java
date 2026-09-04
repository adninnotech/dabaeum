package com.adn.dabaeum.attendance.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * QR 출석 토큰 발급 정책. 유효기간은 현장 사정(스캔까지 걸리는 시간)에 따라 조정할 수 있어야
 * 하므로 코드 상수가 아니라 설정으로 둔다. 출결 창 종료가 더 이르면 그쪽이 우선한다.
 */
@ConfigurationProperties(prefix = "dabaeum.attendance.qr-token")
public record AttendanceQrTokenProperties(
    @DefaultValue("60s") Duration ttl
) {

    public AttendanceQrTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("dabaeum.attendance.qr-token.ttl must be positive");
        }
        if (ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                "dabaeum.attendance.qr-token.ttl must not exceed 1h");
        }
    }
}
