package com.adn.dabaeum.instructor.application;

import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import java.util.UUID;

public record ListInstructorApplicationsQuery(
    UUID institutionId,
    InstructorApplicationStatus status,
    int page,
    int size,
    String sort
) {
}
