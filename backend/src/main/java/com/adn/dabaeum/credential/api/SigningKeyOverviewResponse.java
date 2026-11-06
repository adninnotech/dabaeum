package com.adn.dabaeum.credential.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 관리자 화면의 기관 키 요약 (설계서 §14). 개인키·Token·params_hash 는 싣지 않는다. */
public record SigningKeyOverviewResponse(
    UUID institutionId,
    List<Key> keys,
    List<Operation> operations,
    List<Approval> openApprovals
) {

    public record Key(
        UUID signKeyId, String purpose, int generation, boolean current, String mount, String keyName,
        int currentVersion, String status, String statusReason, Instant rotatedAt, Instant suspendedAt,
        Instant revokedAt, Instant compromisedAt, List<Version> versions
    ) {
    }

    public record Version(
        int keyVersion, String kidFragment, String kid, String status, Map<String, String> publicKeyJwk,
        Instant openbaoCreatedAt, Instant activatedAt, Instant retiredAt, Instant compromisedAt
    ) {
    }

    public record Operation(
        UUID operationId, UUID signKeyId, String purpose, String action, String status, Integer targetKeyVersion,
        Integer expectedVersionBefore, Integer observedVersionAfter, Instant preparedAt, Instant lastCallAt,
        String reason, UUID requestedBy, UUID approvalId, String idempotencyKey, Instant createdAt, Instant updatedAt
    ) {
    }

    public record Approval(
        UUID approvalId, String purpose, String action, UUID signKeyId, Integer expectedVersion,
        Integer targetKeyVersion, Instant targetCompromisedAt, UUID approvedBy, Instant expiresAt, Instant createdAt
    ) {
    }
}
