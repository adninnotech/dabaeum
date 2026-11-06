package com.adn.dabaeum.credential.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** decision 은 RECORDED|FAILED (§15.1). evidence 는 Audit 로그 등 판정 근거. */
public record SigningKeyAdjudicateRequest(
    @NotNull UUID operationId,
    @NotBlank String decision,
    @NotBlank @Size(min = 10, max = 400) String evidence
) {
}
