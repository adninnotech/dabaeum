package com.adn.dabaeum.interest.api;

import com.adn.dabaeum.enrollment.api.CourseCardFields;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.UUID;

public record InterestResponse(
    UUID id,
    UUID courseId,
    Instant createdAt,
    String courseTitle,
    String courseStatus,
    @JsonUnwrapped CourseCardFields card
) {
}
