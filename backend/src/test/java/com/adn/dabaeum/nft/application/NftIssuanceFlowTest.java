package com.adn.dabaeum.nft.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftTargetType;
import com.adn.dabaeum.nft.domain.NftUriMode;
import com.adn.dabaeum.nft.infrastructure.fake.FakeNftLedger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NftIssuanceFlowTest {

    private static final Instant START = Instant.parse("2026-10-28T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");
    private static final String BASE = "https://dabaeum.example";
    private static final String WALLET = "0xWALLETfca";
    private static final String BADGE_CONTRACT = "0xBADGEfca";
    private static final String CERT_CONTRACT = "0xCERTfca";

    private final InMemoryNftStore store = new InMemoryNftStore();
    private final FakeNftLedger ledger = new FakeNftLedger(NOW);
    private final NftMetadataFactory metadata = new NftMetadataFactory(new ObjectMapper(), NftUriMode.URL);
    private final NftRetryPolicy retry = new NftRetryPolicy(3, Duration.ofMinutes(1));

    private NftIssuanceEnqueuer enqueuer(String base) {
        return new NftIssuanceEnqueuer(store, store, metadata, START, base, UUID::randomUUID);
    }

    private NftIssuanceEnqueuer urnEnqueuer(String base) {
        return new NftIssuanceEnqueuer(store, store, new NftMetadataFactory(new ObjectMapper(), NftUriMode.URN),
            START, base, UUID::randomUUID);
    }

    private final NftIssuanceWorker worker = new NftIssuanceWorker(
        store, ledger, WALLET, BADGE_CONTRACT, CERT_CONTRACT, retry);
    private final NftIssuanceReconciler reconciler = new NftIssuanceReconciler(
        store, ledger, WALLET, Duration.ofMinutes(10));

    private static NftSource badge(UUID id, Instant issuedAt) {
        return new NftSource(NftTargetType.BADGE, id, issuedAt, "첫 수료 배지", "첫 강좌를 수료했다",
            null, UUID.fromString("00000000-0000-0000-0000-0000000000f1"), null);
    }

    private void cycle(Instant at) {
        enqueuer(BASE).enqueue(at, 50);
        worker.processBatch(50, at);
        reconciler.reconcileBatch(50, at);
    }

    @Test
    void issuedBadgeIsMintedAndVerifiedOnChain() {
        UUID badgeId = UUID.randomUUID();
        store.issue(badge(badgeId, NOW));

        cycle(NOW);

        NftIssuance row = store.only();
        assertThat(row.status()).isEqualTo(NftIssuanceStatus.MINTED);
        assertThat(row.contractAddress()).isEqualTo(BADGE_CONTRACT);
        assertThat(row.tokenIndex()).isZero();
        assertThat(row.metadataUri()).isEqualTo(BASE + "/api/v1/public/nft/badges/" + row.id());
        assertThat(ledger.token(BADGE_CONTRACT, 0)).hasValueSatisfying(token -> {
            assertThat(token.hash()).isEqualTo(row.metadataHash());
            assertThat(token.owner()).isEqualTo(WALLET);
        });
    }

    @Test
    void credentialGoesToTheCertificateContract() {
        UUID credentialId = UUID.randomUUID();
        store.issue(new NftSource(NftTargetType.CREDENTIAL, credentialId, NOW, "데이터 분석 기초",
            null, "대구평생학습원", null, "e".repeat(64)));

        cycle(NOW);

        NftIssuance row = store.only();
        assertThat(row.status()).isEqualTo(NftIssuanceStatus.MINTED);
        assertThat(row.contractAddress()).isEqualTo(CERT_CONTRACT);
        assertThat(row.metadataUri()).contains("/api/v1/public/nft/certificates/");
    }

    @Test
    void issuesBeforeTheStartTimeAreNeverRecorded() {
        store.issue(badge(UUID.randomUUID(), START.minusSeconds(1)));

        cycle(NOW);

        assertThat(store.all()).isEmpty();
        assertThat(ledger.mintCalls()).isZero();
    }

    @Test
    void withoutADomainNothingIsSentUntilOneIsConfigured() {
        store.issue(badge(UUID.randomUUID(), NOW));

        enqueuer(null).enqueue(NOW, 50);
        worker.processBatch(50, NOW);

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.WAITING_DOMAIN);
        assertThat(ledger.mintCalls()).isZero();

        cycle(NOW.plusSeconds(60));

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.MINTED);
    }

    @Test
    void delayedChainStaysSubmittedThenCompletes() {
        store.issue(badge(UUID.randomUUID(), NOW));
        ledger.nextMint(FakeNftLedger.Outcome.DELAYED);

        cycle(NOW);
        NftIssuance submitted = store.only();
        assertThat(submitted.status()).isEqualTo(NftIssuanceStatus.SUBMITTED);

        ledger.settle(submitted.factHash());
        cycle(NOW.plusSeconds(30));

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.MINTED);
        assertThat(ledger.mintCalls()).isEqualTo(1);
    }

    @Test
    void lostResponseIsNeverResent() {
        store.issue(badge(UUID.randomUUID(), NOW));
        ledger.nextMint(FakeNftLedger.Outcome.RESPONSE_LOST);

        cycle(NOW);
        cycle(NOW.plus(Duration.ofHours(1)));
        cycle(NOW.plus(Duration.ofDays(1)));

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.UNKNOWN);
        assertThat(ledger.mintCalls()).isEqualTo(1);
        assertThat(ledger.totalSupply(BADGE_CONTRACT)).isEqualTo(1L);
    }

    @Test
    void definiteRefusalIsRetriedWithBackoffThenFails() {
        store.issue(badge(UUID.randomUUID(), NOW));
        ledger.nextMint(FakeNftLedger.Outcome.INSUFFICIENT_BALANCE);
        ledger.nextMint(FakeNftLedger.Outcome.INSUFFICIENT_BALANCE);
        ledger.nextMint(FakeNftLedger.Outcome.INSUFFICIENT_BALANCE);

        cycle(NOW);
        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.PENDING);
        assertThat(store.only().nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));

        cycle(NOW.plusSeconds(30));
        assertThat(ledger.mintCalls()).isEqualTo(1);

        cycle(NOW.plus(Duration.ofMinutes(1)));
        cycle(NOW.plus(Duration.ofMinutes(10)));

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.FAILED);
        assertThat(store.only().errorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(ledger.mintCalls()).isEqualTo(3);
        assertThat(ledger.totalSupply(BADGE_CONTRACT)).isZero();
    }

    @Test
    void chainRejectionFailsWithoutRetry() {
        store.issue(badge(UUID.randomUUID(), NOW));
        ledger.nextMint(FakeNftLedger.Outcome.REJECTED);

        cycle(NOW);
        cycle(NOW.plus(Duration.ofHours(1)));

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.FAILED);
        assertThat(ledger.mintCalls()).isEqualTo(1);
    }

    @Test
    void revokedBeforeSubmitIsCancelled() {
        UUID badgeId = UUID.randomUUID();
        store.issue(badge(badgeId, NOW));
        enqueuer(null).enqueue(NOW, 50);

        store.revokeTarget(badgeId);
        cycle(NOW.plusSeconds(60));

        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.CANCELLED);
        assertThat(ledger.mintCalls()).isZero();
    }

    @Test
    void revokedAfterMintKeepsTheTokenAndRecordsTheRevocation() {
        UUID badgeId = UUID.randomUUID();
        store.issue(badge(badgeId, NOW));
        cycle(NOW);

        store.revokeTarget(badgeId);
        cycle(NOW.plusSeconds(60));

        NftIssuance row = store.only();
        assertThat(row.status()).isEqualTo(NftIssuanceStatus.MINTED);
        assertThat(row.revokedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void staleSweepDuringASlowMintStillKeepsTheFactHash() {
        store.issue(badge(UUID.randomUUID(), NOW));
        enqueuer(BASE).enqueue(NOW, 50);
        // mint 가 오래 걸리는 동안 다른 인스턴스의 리컨실러가 전송 중 건을 결과 모름으로 돌린 상황
        NftLedgerPort slow = new DelegatingLedger(ledger) {
            @Override
            public NftMintSubmission mint(NftMintRequest request) {
                reconciler.reconcileBatch(50, NOW.plus(Duration.ofMinutes(11)));
                return super.mint(request);
            }
        };
        new NftIssuanceWorker(store, slow, WALLET, BADGE_CONTRACT, CERT_CONTRACT, retry).processBatch(50, NOW);

        NftIssuance row = store.only();
        assertThat(row.factHash()).isNotNull();
        assertThat(row.status()).isEqualTo(NftIssuanceStatus.SUBMITTED);

        reconciler.reconcileBatch(50, NOW.plus(Duration.ofMinutes(12)));
        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.MINTED);
        assertThat(ledger.mintCalls()).isEqualTo(1);
    }

    @Test
    void aFailingUpdateDoesNotStrandTheRestOfTheBatch() {
        store.issue(badge(UUID.randomUUID(), NOW));
        store.issue(badge(UUID.randomUUID(), NOW.plusMillis(1)));
        enqueuer(BASE).enqueue(NOW, 50);
        InMemoryNftStore flaky = store;
        NftIssuanceRepositoryFailingOnce failing = new NftIssuanceRepositoryFailingOnce(flaky);

        new NftIssuanceWorker(failing, ledger, WALLET, BADGE_CONTRACT, CERT_CONTRACT, retry).processBatch(50, NOW);

        assertThat(ledger.mintCalls()).isEqualTo(2);
        assertThat(store.all()).extracting(NftIssuance::status)
            .containsExactlyInAnyOrder(NftIssuanceStatus.SUBMITTING, NftIssuanceStatus.SUBMITTED);
    }

    @Test
    void unconfirmedRowsDoNotStarveNewerOnes() {
        for (int i = 0; i < 5; i++) {
            store.issue(badge(UUID.randomUUID(), NOW.plusMillis(i)));
            ledger.nextMint(FakeNftLedger.Outcome.DELAYED);
        }
        store.issue(badge(UUID.randomUUID(), NOW.plusMillis(10)));
        ledger.nextMint(FakeNftLedger.Outcome.DELAYED);
        NftIssuanceReconciler small = new NftIssuanceReconciler(store, ledger, WALLET, Duration.ofMinutes(10));
        enqueuer(BASE).enqueue(NOW, 50);
        worker.processBatch(50, NOW);
        NftIssuance newest = store.all().stream().max(java.util.Comparator.comparing(NftIssuance::createdAt))
            .orElseThrow();
        ledger.settle(newest.factHash());

        small.reconcileBatch(2, NOW.plusSeconds(10));
        small.reconcileBatch(2, NOW.plusSeconds(20));
        small.reconcileBatch(2, NOW.plusSeconds(30));

        assertThat(store.findById(newest.id())).map(NftIssuance::status).contains(NftIssuanceStatus.MINTED);
    }

    @Test
    void urnModeMintsWithoutADomainAndVerifiesTheIdentifierOnChain() {
        store.issue(badge(UUID.randomUUID(), NOW));

        urnEnqueuer(null).enqueue(NOW, 50);
        worker.processBatch(50, NOW);
        reconciler.reconcileBatch(50, NOW);

        NftIssuance row = store.only();
        assertThat(row.status()).isEqualTo(NftIssuanceStatus.MINTED);
        assertThat(row.metadataUri()).isEqualTo("urn:dabaeum:nft:badges:" + row.id());
        assertThat(ledger.token(BADGE_CONTRACT, 0)).hasValueSatisfying(token ->
            assertThat(token.uri()).isEqualTo(row.metadataUri()));
    }

    @Test
    void submittingLeftBehindByACrashBecomesUnknown() {
        NftIssuance stuck = NftIssuance.pending(UUID.randomUUID(), NftTargetType.BADGE, UUID.randomUUID(),
            metadata.build(badge(UUID.randomUUID(), NOW), UUID.randomUUID(), BASE), NOW)
            .submitting(BADGE_CONTRACT, NOW);
        store.put(stuck);

        reconciler.reconcileBatch(50, NOW.plus(Duration.ofMinutes(5)));
        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.SUBMITTING);

        reconciler.reconcileBatch(50, NOW.plus(Duration.ofMinutes(11)));
        assertThat(store.only().status()).isEqualTo(NftIssuanceStatus.UNKNOWN);
        assertThat(store.only().errorCode()).isEqualTo("SUBMIT_INTERRUPTED");
    }
}
