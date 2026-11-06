package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftLedgerException;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftToken;
import com.adn.dabaeum.nft.domain.NftTransactionState;
import com.adn.dabaeum.nft.domain.NftTransactionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 보낸 건이 체인에서 진짜 처리됐는지 확인해 장부를 마무리한다.
 * trx_info 로 반영 여부 → nft_idx 로 토큰 번호 → token_info 로 내용 검증 순서다.
 */
public final class NftIssuanceReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NftIssuanceReconciler.class);

    private final NftIssuanceRepository issuances;
    private final NftLedgerPort ledger;
    private final String walletAddress;
    private final Duration submittingStaleAfter;

    public NftIssuanceReconciler(NftIssuanceRepository issuances, NftLedgerPort ledger, String walletAddress,
        Duration submittingStaleAfter) {
        this.issuances = Objects.requireNonNull(issuances, "issuances");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.walletAddress = Objects.requireNonNull(walletAddress, "walletAddress");
        this.submittingStaleAfter = Objects.requireNonNull(submittingStaleAfter, "submittingStaleAfter");
    }

    public void reconcileBatch(int limit, Instant now) {
        for (NftIssuance stuck : issuances.findStaleSubmitting(now.minus(submittingStaleAfter), limit)) {
            LOGGER.warn("NFT 전송 중 상태로 멈춘 건을 결과 모름으로 넘긴다: issuance={}", stuck.id());
            issuances.update(NftIssuanceStatus.SUBMITTING, stuck.unknown("SUBMIT_INTERRUPTED", now));
        }
        for (NftIssuance submitted : issuances.findSubmitted(limit)) {
            try {
                Optional<NftIssuance> next = reconcile(submitted, now);
                if (next.isPresent()) {
                    issuances.update(NftIssuanceStatus.SUBMITTED, next.get());
                } else {
                    issuances.touch(submitted.id(), NftIssuanceStatus.SUBMITTED, now);
                }
            } catch (NftLedgerException exception) {
                // 조회 실패는 다음 차례에 다시 본다. 순서는 뒤로 보낸다.
                LOGGER.warn("NFT 확정 조회 실패: issuance={} code={}", submitted.id(), exception.code());
                issuances.touch(submitted.id(), NftIssuanceStatus.SUBMITTED, now);
            }
        }
    }

    private Optional<NftIssuance> reconcile(NftIssuance issuance, Instant now) {
        NftTransactionState state = ledger.transaction(issuance.factHash());
        if (state.status() == NftTransactionStatus.PENDING) {
            return Optional.empty();
        }
        if (state.status() == NftTransactionStatus.REJECTED) {
            return Optional.of(issuance.failed("CHAIN_REJECTED", now));
        }
        List<Long> indexes = ledger.mintedTokenIndexes(issuance.contractAddress(), issuance.factHash());
        if (indexes.size() != 1) {
            return indexes.isEmpty() ? Optional.empty()
                : Optional.of(issuance.unknown("TOKEN_INDEX_COUNT_" + indexes.size(), now));
        }
        long index = indexes.getFirst();
        Optional<NftToken> token = ledger.token(issuance.contractAddress(), index);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        if (!matches(token.get(), issuance)) {
            return Optional.of(issuance.unknown("TOKEN_MISMATCH", now));
        }
        return Optional.of(issuance.minted(index, state.height(), state.confirmedAt(), now));
    }

    private boolean matches(NftToken token, NftIssuance issuance) {
        return walletAddress.equals(token.owner())
            && issuance.metadataUri().equals(token.uri())
            && issuance.metadataHash().equals(token.hash());
    }
}
