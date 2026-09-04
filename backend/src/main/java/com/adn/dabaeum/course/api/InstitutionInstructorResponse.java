package com.adn.dabaeum.course.api;

import java.time.Instant;
import java.util.UUID;

public record InstitutionInstructorResponse(
    UUID userId,
    String name,
    String email,
    String phone,
    String status,
    Instant joinedAt,
    long courseCount,
    String memo
) {
}
