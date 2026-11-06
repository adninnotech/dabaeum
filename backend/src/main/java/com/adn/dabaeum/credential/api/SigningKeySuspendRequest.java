package com.adn.dabaeum.credential.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SigningKeySuspendRequest(
    @NotBlank @Size(max = 80) String idempotencyKey,
    @NotBlank @Size(max = 1000) String reason
) {
}
