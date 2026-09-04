package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.credential.domain.CredentialStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * 조회 전용 Credential 표현이다. 발급 근거가 된 과정 정보를 함께 담아
 * 수료증 목록에서 어떤 과정의 발급인지 식별할 수 있게 한다.
 */
public record CredentialDetailResponse(
    UUID id,
    UUID credentialGroupId,
    UUID previousCredentialId,
    String credentialNo,
    int versionNo,
    String issuerIdentifier,
    String subjectIdentifier,
    String credentialType,
    CredentialStatus status,
    Instant validFrom,
    Instant validUntil,
    String vcHash,
    Instant issuedAt,
    Instant revokedAt,
    String revocationReason,
    Instant createdAt,
    Instant updatedAt,
    UUID courseId,
    String courseTitle,
    String courseCode,
    String institutionName
) {
}
