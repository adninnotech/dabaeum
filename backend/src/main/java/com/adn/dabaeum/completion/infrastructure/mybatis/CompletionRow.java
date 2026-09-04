package com.adn.dabaeum.completion.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompletionRow(
    UUID id,
    UUID enrollmentId,
    String status,
    BigDecimal attendanceRate,
    Integer completedMinutes,
    BigDecimal creditValue,
    Instant evaluatedAt,
    Instant completedAt,
    UUID confirmedBy,
    Instant confirmedAt,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
}
