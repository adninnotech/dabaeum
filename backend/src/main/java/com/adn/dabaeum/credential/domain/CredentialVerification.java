package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record CredentialVerification(
    UUID id,
    UUID credentialId,
    String presentedCredentialNo,
    String presentedHash,
    String verificationType,
    String requesterType,
    String requesterId,
    CredentialVerificationResult result,
    Instant verifiedAt,
    String requestIp,
    String verificationHash,
    String metadata,
    Instant createdAt
) {

    private static final Pattern HASH = Pattern.compile("^[0-9a-fA-F]{64}$");

    public CredentialVerification {
        if (id == null || result == null || verifiedAt == null || createdAt == null) {
            throw new IllegalArgumentException("verification identity and timestamps are required");
        }
        if (credentialId == null && result != CredentialVerificationResult.NOT_FOUND) {
            throw new IllegalArgumentException("a non-NOT_FOUND result requires a credential");
        }
        if (credentialId != null && result == CredentialVerificationResult.NOT_FOUND) {
            throw new IllegalArgumentException("NOT_FOUND must not contain a credential");
        }
        if ((presentedCredentialNo == null) == (presentedHash == null)) {
            throw new IllegalArgumentException("a presented credential identifier is required");
        }
        if (presentedCredentialNo != null
            && (presentedCredentialNo.isBlank() || presentedCredentialNo.trim().length() > 100)) {
            throw new IllegalArgumentException("presentedCredentialNo must be 1-100 non-blank characters");
        }
        if (presentedHash != null && !HASH.matcher(presentedHash).matches()) {
            throw new IllegalArgumentException("presentedHash must be a SHA-256 hexadecimal value");
        }
        if (verificationType == null || verificationType.isBlank()
            || requesterType == null || requesterType.isBlank()) {
            throw new IllegalArgumentException("verification type and requester type are required");
        }
        if (requesterId != null && requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId must not be blank");
        }
    }
}
