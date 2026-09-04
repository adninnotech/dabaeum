package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.time.Instant;
import java.util.UUID;

/** 공개 가능한 검증 응답이며 해시, IP 주소, 내부 메타데이터는 제외한다. */
public record CredentialVerificationResponse(
    UUID id,
    UUID credentialId,
    String presentedCredentialNo,
    String verificationType,
    String requesterType,
    String requesterId,
    CredentialVerificationResult result,
    Instant verifiedAt,
    Instant createdAt
) {
}
