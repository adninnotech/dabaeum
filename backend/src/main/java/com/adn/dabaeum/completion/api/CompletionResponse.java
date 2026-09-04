package com.adn.dabaeum.completion.api;

import com.adn.dabaeum.completion.domain.CompletionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompletionResponse(
    UUID id,
    UUID enrollmentId,
    CompletionStatus status,
    BigDecimal attendanceRate,
    int completedMinutes,
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
