package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 키 관리 API 의 명령 (설계서 §12.1). 요청자·승인자는 인증된 세션의 사용자 ID 다. */
public final class SigningKeyCommands {

    private SigningKeyCommands() {
    }

    public record CreateKeys(UUID institutionId, UUID requester, String idempotencyKey, String reason) {
        public CreateKeys {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(requester, "requester");
            requireKey(idempotencyKey);
        }
    }

    public record Rotate(UUID institutionId, SignKeyPurpose purpose, UUID requester, UUID approvalId,
        String idempotencyKey) {
        public Rotate {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(approvalId, "approvalId");
            requireKey(idempotencyKey);
        }
    }

    public record Suspend(UUID institutionId, UUID requester, String idempotencyKey, String reason) {
        public Suspend {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(requester, "requester");
            requireKey(idempotencyKey);
        }
    }

    public record Resume(UUID institutionId, UUID requester, UUID approvalId, String idempotencyKey) {
        public Resume {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(approvalId, "approvalId");
            requireKey(idempotencyKey);
        }
    }

    public record Revoke(UUID institutionId, UUID requester, UUID approvalId, String idempotencyKey, String reason) {
        public Revoke {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(approvalId, "approvalId");
            requireKey(idempotencyKey);
        }
    }

    /** 유출 선언 (§6.5, §12.1). keyVersion·compromisedAt 은 승인의 target_* 와 같아야 한다 (§12.2). reason 필수. */
    public record Compromise(UUID institutionId, SignKeyPurpose purpose, UUID requester, UUID approvalId,
        int keyVersion, Instant compromisedAt, String idempotencyKey, String reason) {
        public Compromise {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(approvalId, "approvalId");
            Objects.requireNonNull(compromisedAt, "compromisedAt");
            if (keyVersion < 0) {
                throw new IllegalArgumentException("keyVersion must not be negative");
            }
            requireKey(idempotencyKey);
            if (reason == null || reason.isBlank() || reason.length() > 1000) {
                throw new IllegalArgumentException("reason is required (max 1000)");
            }
        }
    }

    /** 승인 생성. keyVersion·compromisedAt 은 COMPROMISE 에만 있다 (§12.2). */
    public record CreateApproval(UUID institutionId, SignKeyPurpose purpose, KmsAction action, UUID approver,
        String idempotencyKey, Integer keyVersion, Instant compromisedAt, String reason) {
        public CreateApproval {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(approver, "approver");
            requireKey(idempotencyKey);
        }
    }

    /**
     * 운영자 판정 (§15.1). 승인 없이 PLATFORM_ADMIN 단독. evidence 는 Audit 로그 등 판정 근거 10~400자.
     * RECORDED = 호출이 착지했음을 확인했으니 T2 이후를 이어간다. FAILED = 미실행을 확인했으니 새 작업을 허용한다.
     */
    public record Adjudicate(UUID institutionId, SignKeyPurpose purpose, UUID operationId, UUID requester,
        Decision decision, String evidence) {

        public enum Decision {
            RECORDED,
            FAILED
        }

        public Adjudicate {
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(decision, "decision");
            if (evidence == null || evidence.strip().length() < 10 || evidence.strip().length() > 400) {
                throw new IllegalArgumentException("evidence is required (10..400 characters)");
            }
        }
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
            throw new IllegalArgumentException("idempotencyKey is required (max 80)");
        }
    }
}
