package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CourseInstructorAssignRequest(
    @NotNull UUID userId,
    @NotNull CourseInstructorRole role
) {
}
