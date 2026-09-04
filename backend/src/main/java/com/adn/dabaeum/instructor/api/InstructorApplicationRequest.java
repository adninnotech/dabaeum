package com.adn.dabaeum.instructor.api;

import jakarta.validation.constraints.Size;

public record InstructorApplicationRequest(
    @Size(max = 1000) String applicationMessage
) {
}
