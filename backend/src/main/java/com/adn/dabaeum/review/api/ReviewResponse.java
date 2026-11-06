package com.adn.dabaeum.review.api;

import com.adn.dabaeum.enrollment.api.CourseCardFields;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID courseId,
    int rating,
    String content,
    Instant createdAt,
    String courseTitle,
    String userName,
    @JsonUnwrapped CourseCardFields card
) {
}
