package com.adn.dabaeum.enrollment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 수강 철회 정책. 학습자 본인은 수업 시작 {@code selfCutoff} 전까지만 철회할 수 있고, 그 뒤는
 * 수강 결정 권한자(기관 관리자·MAIN 강사·플랫폼 관리자)만 처리한다. 기관 사정에 따라 조정할 수
 * 있어야 하므로 설정으로 둔다. 0 이면 수업 시작 시각까지 본인 철회가 가능하다.
 */
@ConfigurationProperties(prefix = "dabaeum.enrollment.withdrawal")
public record EnrollmentWithdrawalProperties(
    @DefaultValue("24h") Duration selfCutoff
) {

    public EnrollmentWithdrawalProperties {
        if (selfCutoff == null || selfCutoff.isNegative()) {
            throw new IllegalArgumentException(
                "dabaeum.enrollment.withdrawal.self-cutoff must not be negative");
        }
    }
}
