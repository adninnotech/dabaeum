package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import java.util.UUID;

public record CreateEnrollmentCommand(
    UUID courseId,
    UUID userId,
    EnrollmentApplicationType applicationType
) {
}
