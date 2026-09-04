package com.adn.dabaeum.instructor.application;

import java.util.UUID;

public record ApplyInstructorCommand(UUID institutionId, String applicationMessage) {
}
