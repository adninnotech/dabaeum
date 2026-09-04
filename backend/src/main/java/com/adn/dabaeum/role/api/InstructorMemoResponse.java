package com.adn.dabaeum.role.api;

import java.util.UUID;

public record InstructorMemoResponse(
    UUID institutionId,
    UUID userId,
    String memo
) {
}
