package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 기관 서명키 마스터 (설계서 §6.1, §11.1). 기관·목적·세대당 하나이며 현재 세대는 기관·목적당
 * 하나다. 상태 전이는 여기서만 만들고 영속화는 version_no 낙관적 잠금으로 한다.
 */
public record InstitutionSignKey(
    UUID id,
    UUID institutionId,
    SignKeyPurpose purpose,
    int generation,
    boolean isCurrent,
    String mount,
    String keyName,
    String keyType,
    int currentVersion,
    SignKeyStatus status,
    String statusReason,
    Instant rotatedAt,
    Instant suspendedAt,
    Instant revokedAt,
    Instant compromisedAt,
    int versionNo,
    Instant createdAt,
    Instant updatedAt
) {

    public static final String ED25519 = "ed25519";

    public InstitutionSignKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(mount, "mount");
        Objects.requireNonNull(keyName, "keyName");
        Objects.requireNonNull(keyType, "keyType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must not be negative");
        }
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo must be positive");
        }
        if (purpose == SignKeyPurpose.STATUS_LIST
            && (status == SignKeyStatus.SUSPENDED || status == SignKeyStatus.REVOKED)) {
            throw new IllegalStateException("status list keys cannot be suspended or revoked");
        }
        if (status == SignKeyStatus.SUSPENDED && suspendedAt == null) {
            throw new IllegalStateException("suspendedAt is required");
        }
        if (status == SignKeyStatus.REVOKED && (revokedAt == null || statusReason == null)) {
            throw new IllegalStateException("revokedAt and statusReason are required");
        }
        if (status == SignKeyStatus.COMPROMISED && (compromisedAt == null || isCurrent)) {
            throw new IllegalStateException("a compromised key needs compromisedAt and cannot be current");
        }
    }

    /** T1 에서 먼저 INSERT 되는 행. Transit 키는 아직 없다 (§11.4). */
    public static InstitutionSignKey provisioning(
        UUID id, UUID institutionId, SignKeyPurpose purpose, int generation, Instant now
    ) {
        return new InstitutionSignKey(id, institutionId, purpose, generation, true, purpose.mount(),
            SignKeyNaming.keyName(institutionId, generation), ED25519, 0,
            SignKeyStatus.PROVISIONING, null, null, null, null, null, 1, now, now);
    }

    /** 생성·재조회 검증을 통과했다 (§5.3). */
    public InstitutionSignKey activate(int firstVersion, Instant now) {
        require(SignKeyStatus.PROVISIONING, "activate");
        if (firstVersion < 1) {
            throw new IllegalArgumentException("firstVersion must be positive");
        }
        return next(SignKeyStatus.ACTIVE, null, firstVersion, rotatedAt, suspendedAt,
            revokedAt, compromisedAt, isCurrent, now);
    }

    public InstitutionSignKey markError(String reason, Instant now) {
        require(SignKeyStatus.PROVISIONING, "markError");
        return next(SignKeyStatus.ERROR, requireReason(reason), currentVersion, rotatedAt,
            suspendedAt, revokedAt, compromisedAt, isCurrent, now);
    }

    /** 생성 검증 실패로 ERROR 가 된 마스터를 다시 시도한다 (계획 6 결정 11). 키 이름·세대·is_current 는 그대로다. */
    public InstitutionSignKey retryProvisioning(Instant now) {
        require(SignKeyStatus.ERROR, "retryProvisioning");
        return next(SignKeyStatus.PROVISIONING, null, currentVersion, rotatedAt, suspendedAt, revokedAt,
            compromisedAt, isCurrent, now);
    }

    /** §6.2 T2. 버전 행 갱신은 호출자가 별도로 한다. */
    public InstitutionSignKey rotated(int newVersion, Instant now) {
        require(SignKeyStatus.ACTIVE, "rotated");
        if (newVersion != currentVersion + 1) {
            throw new IllegalStateException("rotation must advance the version by one");
        }
        return next(SignKeyStatus.ACTIVE, statusReason, newVersion, now, suspendedAt,
            revokedAt, compromisedAt, isCurrent, now);
    }

    public InstitutionSignKey suspend(String reason, Instant now) {
        requireCredentialPurpose("suspend");
        require(SignKeyStatus.ACTIVE, "suspend");
        return next(SignKeyStatus.SUSPENDED, requireReason(reason), currentVersion, rotatedAt,
            now, revokedAt, compromisedAt, isCurrent, now);
    }

    public InstitutionSignKey resume(Instant now) {
        require(SignKeyStatus.SUSPENDED, "resume");
        return next(SignKeyStatus.ACTIVE, null, currentVersion, rotatedAt, suspendedAt,
            revokedAt, compromisedAt, isCurrent, now);
    }

    /** 기관 폐지. 발급 권한 종료이며 키 자료는 계속 신뢰한다 (§6.1). */
    public InstitutionSignKey revoke(String reason, Instant now) {
        requireCredentialPurpose("revoke");
        if (status != SignKeyStatus.ACTIVE && status != SignKeyStatus.SUSPENDED) {
            throw transition("revoke");
        }
        return next(SignKeyStatus.REVOKED, requireReason(reason), currentVersion, rotatedAt,
            suspendedAt, now, compromisedAt, isCurrent, now);
    }

    /** OpenBao 자체 침해. 모든 버전을 불신하고 현재 세대에서 내려온다 (§6.5). */
    public InstitutionSignKey compromise(String reason, Instant now) {
        if (status == SignKeyStatus.COMPROMISED) {
            throw transition("compromise");
        }
        return next(SignKeyStatus.COMPROMISED, requireReason(reason), currentVersion, rotatedAt,
            suspendedAt, revokedAt, now, false, now);
    }

    public boolean canSign() {
        return status == SignKeyStatus.ACTIVE && isCurrent && currentVersion >= 1;
    }

    private InstitutionSignKey next(
        SignKeyStatus newStatus, String reason, int newCurrentVersion, Instant newRotatedAt,
        Instant newSuspendedAt, Instant newRevokedAt, Instant newCompromisedAt,
        boolean newIsCurrent, Instant now
    ) {
        Objects.requireNonNull(now, "now");
        return new InstitutionSignKey(id, institutionId, purpose, generation, newIsCurrent, mount,
            keyName, keyType, newCurrentVersion, newStatus, reason, newRotatedAt, newSuspendedAt,
            newRevokedAt, newCompromisedAt, versionNo + 1, createdAt, now);
    }

    private void require(SignKeyStatus expected, String action) {
        if (status != expected) {
            throw transition(action);
        }
    }

    private void requireCredentialPurpose(String action) {
        if (purpose != SignKeyPurpose.CREDENTIAL) {
            throw new IllegalStateException(action + " applies to credential keys only");
        }
    }

    private IllegalStateException transition(String action) {
        return new IllegalStateException("cannot " + action + " a sign key in status " + status);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        return reason;
    }
}
