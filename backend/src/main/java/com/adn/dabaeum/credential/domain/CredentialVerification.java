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
    Instant createdAt,
    String issuerName,
    Instant issuedAt
) {

    public CredentialVerification(
        UUID id, UUID credentialId, String presentedCredentialNo, String presentedHash,
        String verificationType, String requesterType, String requesterId,
        CredentialVerificationResult result, Instant verifiedAt, String requestIp,
        String verificationHash, String metadata, Instant createdAt
    ) {
        this(id, credentialId, presentedCredentialNo, presentedHash, verificationType,
            requesterType, requesterId, result, verifiedAt, requestIp, verificationHash,
            metadata, createdAt, null, null);
    }

    private static final Pattern HASH = Pattern.compile("^[0-9a-fA-F]{64}$");
    /** 플랫폼이 보통 규칙으로 판정한 검증 (응답 verificationScope). */
    public static final String PLATFORM = "PLATFORM";
    /** 유출 선언된 키 버전으로 서명됐지만 원장 최초 기록이 유출 이전이라 조건부로 인정한 검증 (설계서 §6.3). */
    public static final String PLATFORM_CONDITIONAL = "PLATFORM_CONDITIONAL";
    private static final Pattern CONDITIONAL_SCOPE =
        Pattern.compile("\"verificationScope\"\\s*:\\s*\"PLATFORM_CONDITIONAL\"");

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

    /** metadata 는 서버가 만든 고정 형식이고, DB 에서 읽으면 jsonb 정규화로 공백이 들어간다. */
    public boolean platformConditional() {
        return metadata != null && CONDITIONAL_SCOPE.matcher(metadata).find();
    }
}
