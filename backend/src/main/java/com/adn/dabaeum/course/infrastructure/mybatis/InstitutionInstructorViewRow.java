package com.adn.dabaeum.course.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InstitutionInstructorViewRow(
    UUID userId,
    String name,
    String email,
    String phone,
    String status,
    Instant joinedAt,
    Long courseCount,
    String memo,
    Instant lastLoginAt,
    LocalDate birthDate
) {
}
