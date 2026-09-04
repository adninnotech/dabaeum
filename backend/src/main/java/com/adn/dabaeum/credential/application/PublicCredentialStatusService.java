package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.time.Instant;

public interface PublicCredentialStatusService {

    Status check(String credentialNo, String requestId);

    record Status(
        String credentialNo,
        CredentialVerificationResult status,
        Instant checkedAt
    ) {
        public Status {
            if (credentialNo == null || credentialNo.isBlank()
                || status == null || checkedAt == null) {
                throw new IllegalArgumentException("Public Credential status fields are required");
            }
        }
    }
}
