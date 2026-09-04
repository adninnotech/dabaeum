package com.adn.dabaeum.course.domain;

import java.time.Instant;
import java.util.UUID;

public record InstitutionInstructorView(
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
