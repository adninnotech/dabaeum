package com.adn.dabaeum.credential.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SigningKeyResumeRequest(
    @NotNull UUID approvalId,
    @NotBlank @Size(max = 80) String idempotencyKey
) {
}
