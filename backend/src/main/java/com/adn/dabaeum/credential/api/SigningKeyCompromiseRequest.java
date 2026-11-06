package com.adn.dabaeum.credential.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** keyVersion·compromisedAt 은 approvalId 승인의 targetKeyVersion·targetCompromisedAt 과 같아야 한다 (§12.2). */
public record SigningKeyCompromiseRequest(
    @NotNull UUID approvalId,
    @NotNull @Min(0) Integer keyVersion,
    @NotNull Instant compromisedAt,
    @NotBlank @Size(max = 80) String idempotencyKey,
    @NotBlank @Size(max = 1000) String reason
) {
}
