package com.adn.dabaeum.completion.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 이수 판정 기준. 출석률이 {@code minAttendanceRate}(%) 미만이면 사유 없이도 미이수로 판정한다.
 * 플랫폼 공통 값이고, 과정별 기준은 아직 두지 않는다.
 */
@ConfigurationProperties(prefix = "dabaeum.completion")
public record CompletionProperties(
    @DefaultValue("80") BigDecimal minAttendanceRate
) {

    public CompletionProperties {
        if (minAttendanceRate == null || minAttendanceRate.signum() < 0
            || minAttendanceRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                "dabaeum.completion.min-attendance-rate must be between 0 and 100");
        }
    }
}
