package com.adn.dabaeum.course.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CourseAttachmentAttachRequest(
    @NotNull UUID fileId
) {
}
