package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import jakarta.validation.constraints.NotNull;

public record CourseInstructorUpdateRequest(
    @NotNull CourseInstructorRole role
) {
}
