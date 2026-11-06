package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 키 관리 작업 기록 (설계서 §11.4, §15.1). 상태 전이는 여기서만 만들고 소유권(owner·lease_until)은 저장소의
 * 조건부 UPDATE 로만 바뀐다. before/after 값은 JSON 텍스트이며 비밀값을 담지 않는다.
 */
public record KmsOperation(
    UUID id,
    UUID signKeyId,
    UUID institutionId,
    SignKeyPurpose purpose,
    KmsAction action,
    KmsOperationStatus status,
    Integer targetKeyVersion,
    Integer expectedVersionBefore,
    Integer observedVersionAfter,
    Instant preparedAt,
    Instant lastCallAt,
    String owner,
    Instant leaseUntil,
    String beforeValue,
    String afterValue,
    String reason,
    UUID requestedBy,
    UUID approvalId,
    String idempotencyKey,
    Instant createdAt,
    Instant updatedAt
) {

    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    public static final int MAX_REASON_LENGTH = 1000;

    public KmsOperation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(signKeyId, "signKeyId");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(preparedAt, "preparedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (idempotencyKey == null || idempotencyKey.isBlank()
            || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("idempotencyKey is required (max 100)");
        }
        if ((owner == null) != (leaseUntil == null)) {
            throw new IllegalArgumentException("owner and leaseUntil must be given together");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason is too long");
        }
    }

    /** §6.2 T1. 외부 호출 전에 커밋되는 행. */
    public static KmsOperation prepared(
        UUID id, InstitutionSignKey master, KmsAction action, Integer targetKeyVersion, String reason,
        UUID requestedBy, UUID approvalId, String idempotencyKey, Instant now
    ) {
        Objects.requireNonNull(master, "master");
        return new KmsOperation(id, master.id(), master.institutionId(), master.purpose(), action,
            KmsOperationStatus.PREPARED, targetKeyVersion, master.currentVersion(), null, now, null, null, null,
            null, null, reason, requestedBy, approvalId, idempotencyKey, now, now);
    }

    /** 외부 호출이 없는 행위(정지·재개·폐지, 후속 없는 유출 선언)는 한 트랜잭션에서 완료로 기록한다. */
    public static KmsOperation completed(
        UUID id, InstitutionSignKey master, KmsAction action, Integer targetKeyVersion, String reason,
        UUID requestedBy, UUID approvalId, String idempotencyKey, String beforeValue, String afterValue, Instant now
    ) {
        Objects.requireNonNull(master, "master");
        return new KmsOperation(id, master.id(), master.institutionId(), master.purpose(), action,
            KmsOperationStatus.COMPLETED, targetKeyVersion, master.currentVersion(), master.currentVersion(), now,
            null, null, null, beforeValue, afterValue, reason, requestedBy, approvalId, idempotencyKey, now, now);
    }

    /** 호출을 보냈으나 결과를 확인하지 못했다. 재호출하지 않는다 (§15.1). */
    public KmsOperation unconfirmed(Instant now) {
        require(KmsOperationStatus.PREPARED, "unconfirmed");
        return with(KmsOperationStatus.UNCONFIRMED, observedVersionAfter, afterValue, reason, now);
    }

    /**
     * §15.1 2단계. 결과 귀속을 확정하지 못한 채 관측한 버전만 남긴다. 상태(PREPARED·UNCONFIRMED)는 그대로다 —
     * PREPARED 는 호출 전일 수도 있으므로 관측했다고 UNCONFIRMED 로 올리지 않는다 (계획 6 결정 6).
     */
    public KmsOperation observed(Integer observedVersion, Instant now) {
        if (status != KmsOperationStatus.PREPARED && status != KmsOperationStatus.UNCONFIRMED) {
            throw transition("observed");
        }
        return with(status, observedVersion, afterValue, reason, now);
    }

    /**
     * 운영자 판정 근거(§15.1)를 사유 뒤에 개행으로 덧붙인다. 합쳐서 1000자를 넘으면 원래 사유 끝을 "…" 로 잘라
     * 근거는 온전히 남긴다. 상태는 바꾸지 않는다.
     */
    public KmsOperation annotated(String note, Instant now) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("note is required");
        }
        if (note.length() > MAX_REASON_LENGTH - 2) {
            throw new IllegalArgumentException("note is too long");
        }
        String base = reason == null ? "" : reason;
        int room = MAX_REASON_LENGTH - note.length() - 1;
        if (base.length() > room) {
            base = base.substring(0, room - 1) + "…";
        }
        String combined = base.isEmpty() ? note : base + "\n" + note;
        return with(status, observedVersionAfter, afterValue, combined, now);
    }

    /** §6.2 T2. OpenBao 결과를 재조회해 DB 에 반영했다. */
    public KmsOperation recorded(Integer observedVersion, String after, Instant now) {
        if (status != KmsOperationStatus.PREPARED && status != KmsOperationStatus.UNCONFIRMED) {
            throw transition("recorded");
        }
        return with(KmsOperationStatus.RECORDED, observedVersion, after, reason, now);
    }

    /** §6.2 T3. */
    public KmsOperation completed(Instant now) {
        require(KmsOperationStatus.RECORDED, "completed");
        return with(KmsOperationStatus.COMPLETED, observedVersionAfter, afterValue, reason, now);
    }

    /** OpenBao 가 명시적으로 실패했거나 아무 것도 만들어지지 않았음을 확인했다. */
    public KmsOperation failed(String failureReason, Instant now) {
        if (status != KmsOperationStatus.PREPARED && status != KmsOperationStatus.UNCONFIRMED) {
            throw transition("failed");
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        return with(KmsOperationStatus.FAILED, observedVersionAfter, afterValue, failureReason, now);
    }

    public boolean inProgress() {
        return status.inProgress();
    }

    private KmsOperation with(
        KmsOperationStatus newStatus, Integer observed, String after, String newReason, Instant now
    ) {
        Objects.requireNonNull(now, "now");
        return new KmsOperation(id, signKeyId, institutionId, purpose, action, newStatus, targetKeyVersion,
            expectedVersionBefore, observed, preparedAt, lastCallAt, owner, leaseUntil, beforeValue, after,
            newReason, requestedBy, approvalId, idempotencyKey, createdAt, now);
    }

    private void require(KmsOperationStatus expected, String name) {
        if (status != expected) {
            throw transition(name);
        }
    }

    private IllegalStateException transition(String name) {
        return new IllegalStateException("cannot " + name + " a kms operation in status " + status);
    }
}
