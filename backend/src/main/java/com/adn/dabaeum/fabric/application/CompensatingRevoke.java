package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import java.time.Instant;
import java.util.UUID;

/**
 * 설계서 §8.1 10단계 보정. 원장에 A 가 올라간 뒤 확정이 거부되면 R 로 되돌리는 요청을 영속화하고 기존 원장
 * 워커가 재시도한다. 수료증은 FAILED 로 남는다.
 */
final class CompensatingRevoke {

    static final String IDEMPOTENCY_PREFIX = "compensate-revoke-";

    private CompensatingRevoke() {
    }

    static BlockchainTransaction of(BlockchainTransaction anchor, String failureCode, Instant now) {
        return new BlockchainTransaction(UUID.randomUUID(), "CREDENTIAL", anchor.referenceId(),
            anchor.network(), BlockchainTransactionType.VC_REVOKE, IDEMPOTENCY_PREFIX + anchor.referenceId(),
            anchor.id(), anchor.requestHash(), null, BlockchainTransactionStatus.PENDING, null, null,
            now, null, 0, null, null, now, now, failureCode);
    }

    /** 워커와 Reconciler 가 같은 수료증을 동시에 거부해도 보정은 하나다. 유니크 제약이 최후 방어선이다. */
    static void insertOnce(
        BlockchainTransactionRepository transactions,
        BlockchainTransaction anchor,
        String failureCode,
        Instant now
    ) {
        BlockchainTransaction revoke = of(anchor, failureCode, now);
        if (transactions.findByIdempotencyKey(revoke.network(), revoke.idempotencyKey()).isEmpty()) {
            transactions.insert(revoke);
        }
    }

    static boolean isCompensation(Credential credential) {
        return credential.status() == CredentialStatus.FAILED
            && (SigningKeyFinalizeCheck.COMPROMISED_AT_FINALIZE.equals(credential.failureCode())
                || SigningKeyFinalizeCheck.INACTIVE_AT_FINALIZE.equals(credential.failureCode()));
    }
}
