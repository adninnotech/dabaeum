package com.adn.dabaeum.credential.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** purpose 는 credential|status-list, action 은 ROTATE|RESUME|REVOKE|COMPROMISE. keyVersion·compromisedAt 은 COMPROMISE 만. */
public record SigningKeyApprovalRequest(
    @NotBlank String purpose,
    @NotBlank String action,
    @NotBlank @Size(max = 80) String idempotencyKey,
    @Min(0) Integer keyVersion,
    Instant compromisedAt,
    @Size(max = 1000) String reason
) {
}
