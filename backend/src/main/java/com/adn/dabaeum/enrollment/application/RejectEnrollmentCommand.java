package com.adn.dabaeum.enrollment.application;

import java.util.UUID;

public record RejectEnrollmentCommand(UUID enrollmentId, String reason) {
}
