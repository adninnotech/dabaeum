package com.adn.dabaeum.nft.infrastructure.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.nft.domain.NftLedgerException;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftTransactionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FakeNftLedgerTest {

    private static final String CONTRACT = "0xFAKEBADGEfca";
    private static final String WALLET = "0xFAKEWALLETfca";
    private static final String HASH = "a".repeat(64);
    private static final NftMintRequest REQUEST =
        new NftMintRequest(CONTRACT, WALLET, "https://dabaeum.test/nft/1", HASH, WALLET);

    private final FakeNftLedger ledger = new FakeNftLedger(Instant.parse("2026-10-28T00:00:00Z"));

    @Test
    void confirmedMintIsVisibleThroughEveryReadApi() {
        NftMintSubmission submission = ledger.mint(REQUEST);

        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.CONFIRMED);
        assertThat(ledger.transaction(submission.factHash()).status()).isEqualTo(NftTransactionStatus.CONFIRMED);
        assertThat(ledger.mintedTokenIndexes(CONTRACT, submission.factHash())).containsExactly(0L);
        assertThat(ledger.token(CONTRACT, 0)).hasValueSatisfying(token -> {
            assertThat(token.owner()).isEqualTo(WALLET);
            assertThat(token.hash()).isEqualTo(HASH);
        });
        assertThat(ledger.totalSupply(CONTRACT)).isEqualTo(1L);
    }

    @Test
    void delayedMintStaysPendingUntilTheChainCatchesUp() {
        ledger.nextMint(FakeNftLedger.Outcome.DELAYED);

        NftMintSubmission submission = ledger.mint(REQUEST);

        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.PENDING);
        assertThat(ledger.transaction(submission.factHash()).status()).isEqualTo(NftTransactionStatus.PENDING);
        assertThat(ledger.totalSupply(CONTRACT)).isZero();

        ledger.settle(submission.factHash());

        assertThat(ledger.transaction(submission.factHash()).status()).isEqualTo(NftTransactionStatus.CONFIRMED);
        assertThat(ledger.totalSupply(CONTRACT)).isEqualTo(1L);
    }

    @Test
    void lostResponseStillMintsOnChainButTheCallerGetsOutcomeUnknown() {
        ledger.nextMint(FakeNftLedger.Outcome.RESPONSE_LOST);

        assertThatThrownBy(() -> ledger.mint(REQUEST))
            .isInstanceOfSatisfying(NftLedgerException.class,
                exception -> assertThat(exception.code()).isEqualTo(NftLedgerException.OUTCOME_UNKNOWN));
        assertThat(ledger.totalSupply(CONTRACT)).isEqualTo(1L);
    }

    @Test
    void refusalsWriteNothing() {
        ledger.nextMint(FakeNftLedger.Outcome.INSUFFICIENT_BALANCE);

        assertThatThrownBy(() -> ledger.mint(REQUEST))
            .isInstanceOfSatisfying(NftLedgerException.class,
                exception -> assertThat(exception.code()).isEqualTo(NftLedgerException.INSUFFICIENT_BALANCE));
        assertThat(ledger.totalSupply(CONTRACT)).isZero();
        assertThat(ledger.mintCalls()).isEqualTo(1);
    }
}
