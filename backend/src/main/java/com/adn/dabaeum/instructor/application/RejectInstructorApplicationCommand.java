package com.adn.dabaeum.instructor.application;

import java.util.UUID;

public record RejectInstructorApplicationCommand(
    UUID applicationId,
    String rejectionReason
) {
}
