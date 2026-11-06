package com.adn.dabaeum.credential.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 복구 워커 설정 (§15.1, §16). fixed-delay 는 lease(5분)와 같은 크기가 기본이다. */
@ConfigurationProperties(prefix = "dabaeum.kms.recovery")
public record KmsRecoveryProperties(
    boolean enabled,
    Duration initialDelay,
    Duration fixedDelay,
    int batchSize
) {
    public KmsRecoveryProperties {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("KMS recovery initial delay must not be negative");
        }
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("KMS recovery fixed delay must be positive");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("KMS recovery batch size must be between 1 and 100");
        }
    }
}
