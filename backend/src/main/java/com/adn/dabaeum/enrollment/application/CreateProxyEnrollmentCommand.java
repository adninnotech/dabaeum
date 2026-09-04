package com.adn.dabaeum.enrollment.application;

import java.util.UUID;

public record CreateProxyEnrollmentCommand(UUID courseId, UUID userId) {
}
