package com.adn.dabaeum.instructor.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstructorApplicationRejectRequest(
    @NotBlank @Size(max = 1000) String rejectionReason
) {
}
