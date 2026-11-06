package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.util.UUID;

public record AttendanceLearnerRow(UUID enrollmentId, UUID userId, String userName) {
}
