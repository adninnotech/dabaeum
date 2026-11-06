package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §8.1 10단계 보정: 원장 A 를 R 로 되돌리는 요청을 영속화한다. */
class CompensatingRevokeTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID CREDENTIAL = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void 보정_요청은_같은_네트워크의_PENDING_VC_REVOKE_이고_사유는_실패_코드다() {
        BlockchainTransaction anchor = anchor();

        BlockchainTransaction revoke = CompensatingRevoke.of(anchor, "COMPROMISED_AT_FINALIZE", NOW);

        assertThat(revoke.transactionType()).isEqualTo(BlockchainTransactionType.VC_REVOKE);
        assertThat(revoke.status()).isEqualTo(BlockchainTransactionStatus.PENDING);
        assertThat(revoke.referenceId()).isEqualTo(CREDENTIAL);
        assertThat(revoke.network()).isEqualTo(anchor.network());
        assertThat(revoke.idempotencyKey()).isEqualTo("compensate-revoke-" + CREDENTIAL);
        assertThat(revoke.correlationId()).isEqualTo(anchor.id());
        assertThat(revoke.operationReason()).isEqualTo("COMPROMISED_AT_FINALIZE");
    }

    @Test
    void 같은_수료증의_보정은_한_번만_넣는다() {
        RecordingTransactions transactions = new RecordingTransactions();

        CompensatingRevoke.insertOnce(transactions, anchor(), "COMPROMISED_AT_FINALIZE", NOW);
        CompensatingRevoke.insertOnce(transactions, anchor(), "COMPROMISED_AT_FINALIZE", NOW.plusSeconds(1));

        assertThat(transactions.inserted).hasSize(1);
    }

    @Test
    void 확정에서_거부된_FAILED_수료증만_보정_대상이다() {
        assertThat(CompensatingRevoke.isCompensation(failed("COMPROMISED_AT_FINALIZE"))).isTrue();
        assertThat(CompensatingRevoke.isCompensation(failed("SIGNING_KEY_INACTIVE_AT_FINALIZE"))).isTrue();
        assertThat(CompensatingRevoke.isCompensation(failed("CREDENTIAL_PROOF_GENERATION_FAILED"))).isFalse();
    }

    @Test
    void FAILED_가_아닌_수료증은_보정_대상이_아니다() {
        // Credential 불변식이 FAILED 가 아닌 상태에 failure_code 를 허용하지 않으므로, isCompensation 의 FAILED 조건은
        // 합법적인 Credential 로는 코드 조건과 따로 반증할 수 없다. 이 테스트는 비FAILED 행이 보정으로 판정되지 않음만 고정한다.
        Credential issuing = new Credential(CREDENTIAL, UUID.randomUUID(), null, "CERT-1", 1, null, null,
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUING, null, null, null, null, null, null,
            null, null, null, null, null, NOW, NOW);

        assertThat(CompensatingRevoke.isCompensation(issuing)).isFalse();
    }

    private static Credential failed(String code) {
        return new Credential(CREDENTIAL, UUID.randomUUID(), null, "CERT-1", 1, null, null,
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.FAILED, null, null, null, null, null, null,
            null, null, null, code, "failed", NOW, NOW);
    }

    private static BlockchainTransaction anchor() {
        return new BlockchainTransaction(UUID.fromString("30000000-0000-0000-0000-000000000003"), "CREDENTIAL",
            CREDENTIAL, "DAEGUCHAIN", BlockchainTransactionType.VC_ANCHOR, "anchor-" + CREDENTIAL,
            UUID.randomUUID(), "a".repeat(64), null, BlockchainTransactionStatus.PROCESSING, null, null,
            NOW, null, 0, null, null, NOW, NOW, null);
    }

    private static final class RecordingTransactions implements BlockchainTransactionRepository {
        final List<BlockchainTransaction> inserted = new ArrayList<>();

        @Override public List<BlockchainTransaction> claimDue(List<String> networks, int limit, Instant now) {
            throw new UnsupportedOperationException();
        }
        @Override public List<BlockchainTransaction> claimStale(
            List<String> networks, int limit, Instant now, Instant staleBefore) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<BlockchainTransaction> findById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public Optional<BlockchainTransaction> findByIdempotencyKey(String network, String key) {
            return inserted.stream().filter(value -> value.network().equals(network)
                && value.idempotencyKey().equals(key)).findFirst();
        }
        @Override public void insert(BlockchainTransaction transaction) { inserted.add(transaction); }
        @Override public void update(BlockchainTransaction transaction) { throw new UnsupportedOperationException(); }
    }
}
