package com.adn.dabaeum.completion.application;

import java.math.BigDecimal;
import java.util.UUID;

public record EvaluateCompletionCommand(
    UUID enrollmentId,
    BigDecimal attendanceRate,
    Integer completedMinutes,
    BigDecimal creditValue,
    String failureReason
) {
}
