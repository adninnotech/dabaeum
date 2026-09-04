package com.adn.dabaeum.attendance.application;

import java.time.Instant;
import java.util.UUID;

public interface AttendanceMetricsQuery {

    AttendanceMetrics calculate(UUID enrollmentId, Instant evaluatedAt);
}
