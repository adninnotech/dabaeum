package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftLedgerException;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftTransactionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 대기 건을 집어 mint 를 보내고 영수증(fact hash)을 적는다. 확정은 리컨실러가 한다.
 *
 * <p>mainnet 중복 NFT 는 지울 수 없으므로, 체인에 아무것도 안 생긴 것이 확실한 실패만 다시 보낸다.
 * 결과를 모르면 UNKNOWN 으로 두고 운영자가 판정한다.
 */
public final class NftIssuanceWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NftIssuanceWorker.class);

    private final NftIssuanceRepository issuances;
    private final NftLedgerPort ledger;
    private final String walletAddress;
    private final String badgeContract;
    private final String credentialContract;
    private final NftRetryPolicy retry;

    public NftIssuanceWorker(NftIssuanceRepository issuances, NftLedgerPort ledger, String walletAddress,
        String badgeContract, String credentialContract, NftRetryPolicy retry) {
        this.issuances = Objects.requireNonNull(issuances, "issuances");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.walletAddress = required(walletAddress, "walletAddress");
        this.badgeContract = required(badgeContract, "badgeContract");
        this.credentialContract = required(credentialContract, "credentialContract");
        this.retry = Objects.requireNonNull(retry, "retry");
    }

    /**
     * 한 번에 한 건씩 집는다. 여러 건을 한꺼번에 집으면 앞 건의 mint 가 오래 걸리는 동안 뒤 건이
     * "전송 중" 으로 멈춘 것처럼 보이고, 한 건의 기록 실패가 나머지를 묶어 둔다.
     */
    public void processBatch(int limit, Instant now) {
        for (int sent = 0; sent < limit; sent++) {
            List<NftIssuance> claimed = issuances.claimDue(1, now, badgeContract, credentialContract);
            if (claimed.isEmpty()) {
                return;
            }
            NftIssuance next = submit(claimed.getFirst(), now);
            try {
                record(next);
            } catch (RuntimeException exception) {
                // 전송 중으로 남은 건은 리컨실러가 결과 모름으로 넘긴다. 영수증은 로그에만 남는다.
                LOGGER.error("NFT 발행 결과를 기록하지 못했다: issuance={} status={} factHash={}",
                    next.id(), next.status(), next.factHash(), exception);
            }
        }
    }

    /**
     * 기록할 때 이미 다른 리컨실러가 "전송 중 정체" 로 결과 모름 처리했을 수 있다. 영수증을 받았다면
     * 그 상태에서도 받아 적어, 체인에 생긴 토큰과 장부의 연결을 잃지 않는다.
     */
    private void record(NftIssuance next) {
        if (issuances.update(NftIssuanceStatus.SUBMITTING, next)) {
            return;
        }
        if (next.factHash() != null && issuances.update(NftIssuanceStatus.UNKNOWN, next)) {
            LOGGER.warn("결과 모름으로 넘어간 건에 영수증을 다시 적었다: issuance={} factHash={}",
                next.id(), next.factHash());
            return;
        }
        LOGGER.error("NFT 발행 결과를 기록하지 못했다(상태가 바뀜): issuance={} status={} factHash={}",
            next.id(), next.status(), next.factHash());
    }

    private NftIssuance submit(NftIssuance issuance, Instant now) {
        if (issuance.revoked()) {
            return issuance.cancelled(now);
        }
        try {
            NftMintSubmission submission = ledger.mint(new NftMintRequest(issuance.contractAddress(),
                walletAddress, issuance.metadataUri(), issuance.metadataHash(), walletAddress));
            if (submission.state().status() == NftTransactionStatus.REJECTED) {
                return issuance.submitted(submission.factHash(), now).failed("CHAIN_REJECTED", now);
            }
            return issuance.submitted(submission.factHash(), now);
        } catch (NftLedgerException exception) {
            return afterFailure(issuance, exception, now);
        } catch (RuntimeException exception) {
            // 보냈는지 모르는 예외는 결과 모름으로 둔다.
            LOGGER.warn("NFT 발행 중 예상하지 못한 오류: issuance={} type={}", issuance.id(),
                exception.getClass().getName(), exception);
            return issuance.unknown("UNEXPECTED", now);
        }
    }

    private NftIssuance afterFailure(NftIssuance issuance, NftLedgerException exception, Instant now) {
        LOGGER.warn("NFT 발행 실패: issuance={} code={}", issuance.id(), exception.code());
        if (!exception.retryable() && !exception.definiteRefusal()) {
            return issuance.unknown(exception.code(), now);
        }
        int attempt = issuance.retryCount() + 1;
        if (!retry.canRetryAfter(attempt)) {
            return issuance.failed(exception.code(), now);
        }
        return issuance.retryLater(exception.code(), retry.nextAttempt(attempt, now), now);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
