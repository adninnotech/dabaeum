package com.adn.dabaeum.credential.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 서버 승인 기록 (설계서 §11.5, §12.2). 대상 마스터(세대)·현재 버전·유출 대상 버전·사고 시각·실행 파라미터
 * 해시를 승인 시점에 고정하고, 실행은 저장소의 원자 소비 UPDATE 가 전부 대조한다.
 */
public record KmsApproval(
    UUID id,
    UUID institutionId,
    SignKeyPurpose purpose,
    KmsAction action,
    UUID signKeyId,
    Integer expectedVersion,
    Integer targetKeyVersion,
    Instant targetCompromisedAt,
    String paramsHash,
    UUID approvedBy,
    Instant expiresAt,
    Instant consumedAt,
    UUID consumedByOperationId,
    Instant createdAt
) {

    public static final Duration DEFAULT_VALIDITY = Duration.ofHours(24);
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public KmsApproval {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(signKeyId, "signKeyId");
        Objects.requireNonNull(approvedBy, "approvedBy");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!requiresApproval(action)) {
            throw new IllegalArgumentException(action + " does not take an approval");
        }
        if (paramsHash == null || !SHA256_HEX.matcher(paramsHash).matches()) {
            throw new IllegalArgumentException("paramsHash must be 64 lowercase hex characters");
        }
        boolean compromise = action == KmsAction.COMPROMISE;
        if (compromise && (targetKeyVersion == null || targetCompromisedAt == null)) {
            throw new IllegalArgumentException("COMPROMISE requires targetKeyVersion and targetCompromisedAt");
        }
        if (!compromise && (targetKeyVersion != null || targetCompromisedAt != null)) {
            throw new IllegalArgumentException(action + " must not carry compromise targets");
        }
        if ((consumedAt == null) != (consumedByOperationId == null)) {
            throw new IllegalArgumentException("consumedAt and consumedByOperationId must be given together");
        }
    }

    public static boolean requiresApproval(KmsAction action) {
        return action == KmsAction.ROTATE || action == KmsAction.RESUME
            || action == KmsAction.REVOKE || action == KmsAction.COMPROMISE;
    }

    /** 승인자가 인증된 세션으로 만든다. expected_version 은 지금의 current_version 이다 (§12.2). */
    public static KmsApproval create(
        UUID id, InstitutionSignKey master, KmsAction action, Integer targetKeyVersion,
        Instant targetCompromisedAt, String paramsHash, UUID approvedBy, Instant now
    ) {
        Objects.requireNonNull(master, "master");
        Objects.requireNonNull(now, "now");
        return new KmsApproval(id, master.institutionId(), master.purpose(), action, master.id(),
            master.currentVersion(), targetKeyVersion, targetCompromisedAt, paramsHash, approvedBy,
            now.plus(DEFAULT_VALIDITY), null, null, now);
    }

    /** 미사용이고 만료 전이다 (관리자 화면의 "미사용 승인", §14). */
    public boolean isOpen(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
