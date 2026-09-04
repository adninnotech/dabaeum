package com.adn.dabaeum.fabric.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.fabric.worker")
public record FabricWorkerProperties(
    boolean enabled,
    Duration fixedDelay,
    int batchSize
) {
    public FabricWorkerProperties {
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("Fabric Worker fixed delay must be positive");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Fabric Worker batch size must be between 1 and 100");
        }
    }
}
