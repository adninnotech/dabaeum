package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Credential(
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
    String vcPayload,
    String vcHash,
    String chainKey,
    String vcHashVersion,
    Instant issuedAt,
    Instant revokedAt,
    String revocationReason,
    String failureCode,
    String failureMessage,
    Instant createdAt,
    Instant updatedAt
) {

    private static final Pattern VC_HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern CHAIN_KEY_PATTERN = Pattern.compile("^[A-Z0-9]{16}$");

    public Credential(
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
        String vcPayload,
        String vcHash,
        Instant issuedAt,
        Instant revokedAt,
        String revocationReason,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, credentialGroupId, previousCredentialId, credentialNo, versionNo,
            issuerIdentifier, subjectIdentifier, credentialType, status, validFrom, validUntil,
            vcPayload, vcHash, null, null, issuedAt, revokedAt, revocationReason, failureCode,
            failureMessage, createdAt, updatedAt);
    }

    public Credential {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(credentialGroupId, "credentialGroupId");
        credentialNo = requireNonBlank(credentialNo, "credentialNo");
        credentialType = requireNonBlank(credentialType, "credentialType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo must be positive");
        }
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }
        issuerIdentifier = normalizeOptional(issuerIdentifier, "issuerIdentifier");
        subjectIdentifier = normalizeOptional(subjectIdentifier, "subjectIdentifier");
        vcPayload = preserveOptionalNonBlank(vcPayload, "vcPayload");
        vcHash = normalizeOptional(vcHash, "vcHash");
        chainKey = normalizeOptional(chainKey, "chainKey");
        vcHashVersion = normalizeOptional(vcHashVersion, "vcHashVersion");
        revocationReason = normalizeOptional(revocationReason, "revocationReason");
        failureCode = normalizeOptional(failureCode, "failureCode");
        failureMessage = normalizeOptional(failureMessage, "failureMessage");
        if (vcHash != null && !VC_HASH_PATTERN.matcher(vcHash).matches()) {
            throw new IllegalArgumentException("vcHash must be 64 lowercase hexadecimal characters");
        }
        if (chainKey != null && !CHAIN_KEY_PATTERN.matcher(chainKey).matches()) {
            throw new IllegalArgumentException("chainKey must be 16 uppercase alphanumeric characters");
        }
        if (vcHashVersion != null
            && !vcHashVersion.equals("ENVELOPE_SHA256_V0")
            && !vcHashVersion.equals("COMPACT_JWS_SHA256_V1")) {
            throw new IllegalArgumentException("vcHashVersion is unsupported");
        }
        boolean legacyMetadata = chainKey == null
            && (vcHashVersion == null || vcHashVersion.equals("ENVELOPE_SHA256_V0"));
        boolean currentMetadata = chainKey != null
            && "COMPACT_JWS_SHA256_V1".equals(vcHashVersion);
        if (!legacyMetadata && !currentMetadata) {
            throw new IllegalArgumentException("chainKey and vcHashVersion are inconsistent");
        }
        if (validUntil != null && validFrom != null && !validFrom.isBefore(validUntil)) {
            throw new IllegalArgumentException("validFrom must be before validUntil");
        }
        validateState(status, issuerIdentifier, subjectIdentifier, validFrom, vcPayload, vcHash,
            issuedAt, revokedAt, revocationReason, failureCode, failureMessage);
    }

    public Credential startIssuing(Instant nextUpdatedAt) {
        requireState(CredentialStatus.PENDING);
        return copy(CredentialStatus.ISSUING, issuerIdentifier, subjectIdentifier, validFrom,
            vcPayload, vcHash, issuedAt, revokedAt, revocationReason, failureCode, failureMessage,
            requireUpdatedAt(nextUpdatedAt));
    }

    public Credential markIssued(
        String nextVcPayload,
        String nextVcHash,
        String nextIssuerIdentifier,
        String nextSubjectIdentifier,
        Instant nextValidFrom,
        Instant nextIssuedAt
    ) {
        requireState(CredentialStatus.ISSUING);
        return copy(CredentialStatus.ISSUED, nextIssuerIdentifier, nextSubjectIdentifier,
            nextValidFrom, nextVcPayload, nextVcHash, nextIssuedAt, null, null, null, null,
            requireUpdatedAt(nextIssuedAt));
    }

    public Credential markFailed(String nextFailureCode, String nextFailureMessage, Instant nextUpdatedAt) {
        requireState(CredentialStatus.ISSUING);
        return copy(CredentialStatus.FAILED, null, null, null, null, null, null, null, null,
            nextFailureCode, nextFailureMessage, requireUpdatedAt(nextUpdatedAt));
    }

    public Credential markRevoked(String nextRevocationReason, Instant nextRevokedAt) {
        requireState(CredentialStatus.ISSUED);
        return copy(CredentialStatus.REVOKED, issuerIdentifier, subjectIdentifier, validFrom,
            vcPayload, vcHash, issuedAt, nextRevokedAt, nextRevocationReason, null, null,
            requireUpdatedAt(nextRevokedAt));
    }

    public Credential markSuperseded(Instant nextUpdatedAt) {
        requireState(CredentialStatus.ISSUED);
        return copy(CredentialStatus.SUPERSEDED, issuerIdentifier, subjectIdentifier, validFrom,
            vcPayload, vcHash, issuedAt, null, null, null, null, requireUpdatedAt(nextUpdatedAt));
    }

    public Credential markExpired(Instant nextUpdatedAt) {
        requireState(CredentialStatus.ISSUED);
        return copy(CredentialStatus.EXPIRED, issuerIdentifier, subjectIdentifier, validFrom,
            vcPayload, vcHash, issuedAt, null, null, null, null, requireUpdatedAt(nextUpdatedAt));
    }

    private Credential copy(
        CredentialStatus nextStatus,
        String nextIssuerIdentifier,
        String nextSubjectIdentifier,
        Instant nextValidFrom,
        String nextVcPayload,
        String nextVcHash,
        Instant nextIssuedAt,
        Instant nextRevokedAt,
        String nextRevocationReason,
        String nextFailureCode,
        String nextFailureMessage,
        Instant nextUpdatedAt
    ) {
        return new Credential(id, credentialGroupId, previousCredentialId, credentialNo, versionNo,
            nextIssuerIdentifier, nextSubjectIdentifier, credentialType, nextStatus, nextValidFrom,
            validUntil, nextVcPayload, nextVcHash, chainKey, vcHashVersion, nextIssuedAt, nextRevokedAt,
            nextRevocationReason, nextFailureCode, nextFailureMessage, createdAt, nextUpdatedAt);
    }

    private void requireState(CredentialStatus expected) {
        if (status != expected) {
            throw new StateConflictException();
        }
    }

    private Instant requireUpdatedAt(Instant value) {
        Objects.requireNonNull(value, "updatedAt");
        if (value.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        return value;
    }

    private static void validateState(
        CredentialStatus state,
        String issuerIdentifier,
        String subjectIdentifier,
        Instant validFrom,
        String vcPayload,
        String vcHash,
        Instant issuedAt,
        Instant revokedAt,
        String revocationReason,
        String failureCode,
        String failureMessage
    ) {
        switch (state) {
            case PENDING -> requireAllNull(issuerIdentifier, subjectIdentifier, validFrom,
                vcPayload, vcHash, issuedAt, revokedAt, revocationReason, failureCode, failureMessage,
                "pending credential must not have outcome data");
            case ISSUING -> {
                requireAllNull(revokedAt, revocationReason, failureCode, failureMessage,
                    "issuing credential must not have terminal data");
                if ((vcPayload == null) != (vcHash == null)) {
                    throw new IllegalArgumentException(
                        "issuing credential envelope and hash must be stored together");
                }
            }
            case ISSUED, SUPERSEDED, EXPIRED -> {
                requireIssuedValues(issuerIdentifier, subjectIdentifier, validFrom, vcPayload, vcHash,
                    issuedAt);
                requireAllNull(revokedAt, revocationReason, failureCode, failureMessage,
                    "issued credential must not have revocation or failure data");
            }
            case FAILED -> {
                requireAllNull(issuerIdentifier, subjectIdentifier, validFrom, vcPayload, vcHash, issuedAt,
                    revokedAt, revocationReason, "failed credential must not have issuance data");
                requireNonBlank(failureCode, "failed credential requires failureCode");
            }
            case REVOKED -> {
                requireIssuedValues(issuerIdentifier, subjectIdentifier, validFrom, vcPayload, vcHash,
                    issuedAt);
                requireNonNull(revokedAt, "revoked credential requires revokedAt");
                requireNonBlank(revocationReason, "revoked credential requires revocationReason");
                requireAllNull(failureCode, failureMessage,
                    "revoked credential must not have failure data");
            }
        }
    }

    private static void requireIssuedValues(
        String issuerIdentifier,
        String subjectIdentifier,
        Instant validFrom,
        String vcPayload,
        String vcHash,
        Instant issuedAt
    ) {
        requireNonBlank(issuerIdentifier, "issued credential requires issuerIdentifier");
        requireNonBlank(subjectIdentifier, "issued credential requires subjectIdentifier");
        requireNonNull(validFrom, "issued credential requires validFrom");
        requireNonBlank(vcPayload, "issued credential requires vcPayload");
        requireNonBlank(vcHash, "issued credential requires vcHash");
        requireNonNull(issuedAt, "issued credential requires issuedAt");
    }

    private static String normalizeOptional(String value, String name) {
        return value == null ? null : requireNonBlank(value, name);
    }

    private static String preserveOptionalNonBlank(String value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message + " is required");
        }
        return value.trim();
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAllNull(Object first, Object second, String message) {
        if (first != null || second != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAllNull(
        Object first, Object second, Object third, Object fourth, String message
    ) {
        if (first != null || second != null || third != null || fourth != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAllNull(
        Object first, Object second, Object third, Object fourth, Object fifth, Object sixth,
        Object seventh, Object eighth, Object ninth, Object tenth, String message
    ) {
        if (first != null || second != null || third != null || fourth != null || fifth != null
            || sixth != null || seventh != null || eighth != null || ninth != null || tenth != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAllNull(
        Object first, Object second, Object third, Object fourth, Object fifth, Object sixth,
        Object seventh, Object eighth, String message
    ) {
        if (first != null || second != null || third != null || fourth != null || fifth != null
            || sixth != null || seventh != null || eighth != null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static final class StateConflictException extends IllegalStateException {

        public StateConflictException() {
            super("CREDENTIAL_STATE_CONFLICT");
        }
    }
}
