package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.time.Instant;

public record PublicCredentialStatusResponse(
    String credentialNo,
    CredentialVerificationResult status,
    Instant checkedAt
) {
}
