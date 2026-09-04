package com.adn.dabaeum.correction.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record AdminCorrectionRow(
    UUID id,
    String targetType,
    UUID targetId,
    String fromStatus,
    String toStatus,
    String reason,
    UUID correctedBy,
    Instant correctedAt
) {
}
