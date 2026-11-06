package com.adn.dabaeum.nft.infrastructure.fake;

import com.adn.dabaeum.nft.domain.NftAmount;
import com.adn.dabaeum.nft.domain.NftLedgerException;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftToken;
import com.adn.dabaeum.nft.domain.NftTransactionState;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 NFT 원장. 자동 테스트와 로컬 개발용이며 체인에 아무것도 쓰지 않는다.
 * {@link #nextMint(Outcome)} 로 체인 지연·응답 유실·거절을 재현한다.
 */
public final class FakeNftLedger implements NftLedgerPort {

    public enum Outcome { CONFIRMED, DELAYED, RESPONSE_LOST, REJECTED, INSUFFICIENT_BALANCE }

    private record Pending(NftMintRequest request) { }

    private final Instant clock;
    private final Deque<Outcome> outcomes = new ArrayDeque<>();
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, NftTransactionState> transactions = new HashMap<>();
    private final Map<String, List<Long>> indexesByFact = new HashMap<>();
    private final Map<String, List<NftToken>> tokensByContract = new HashMap<>();
    private long height = 1;
    private int factSequence;
    private int mintCalls;

    public FakeNftLedger(Instant clock) {
        this.clock = clock;
    }

    public synchronized void nextMint(Outcome outcome) {
        outcomes.addLast(outcome);
    }

    /** 지연된 발행을 체인에 반영한다. */
    public synchronized void settle(String factHash) {
        Pending delayed = pending.remove(factHash);
        if (delayed != null) {
            record(factHash, delayed.request());
        }
    }

    public synchronized int mintCalls() {
        return mintCalls;
    }

    @Override
    public synchronized NftMintSubmission mint(NftMintRequest request) {
        mintCalls++;
        Outcome outcome = outcomes.isEmpty() ? Outcome.CONFIRMED : outcomes.removeFirst();
        if (outcome == Outcome.INSUFFICIENT_BALANCE) {
            throw new NftLedgerException(NftLedgerException.INSUFFICIENT_BALANCE, "fake balance is empty");
        }
        String factHash = "FAKEFACT" + (++factSequence);
        return switch (outcome) {
            case CONFIRMED -> new NftMintSubmission(factHash, record(factHash, request));
            case DELAYED -> {
                pending.put(factHash, new Pending(request));
                yield new NftMintSubmission(factHash, NftTransactionState.pending());
            }
            case RESPONSE_LOST -> {
                record(factHash, request);
                throw new NftLedgerException(NftLedgerException.OUTCOME_UNKNOWN, "fake response lost");
            }
            case REJECTED -> {
                NftTransactionState rejected = NftTransactionState.rejected("fake rejection");
                transactions.put(factHash, rejected);
                yield new NftMintSubmission(factHash, rejected);
            }
            case INSUFFICIENT_BALANCE -> throw new IllegalStateException("unreachable");
        };
    }

    @Override
    public synchronized NftTransactionState transaction(String factHash) {
        return transactions.getOrDefault(factHash, NftTransactionState.pending());
    }

    @Override
    public synchronized List<Long> mintedTokenIndexes(String contractAddress, String factHash) {
        return indexesByFact.getOrDefault(factHash, List.of());
    }

    @Override
    public synchronized Optional<NftToken> token(String contractAddress, long index) {
        List<NftToken> tokens = tokensByContract.getOrDefault(contractAddress, List.of());
        return index >= 0 && index < tokens.size() ? Optional.of(tokens.get((int) index)) : Optional.empty();
    }

    @Override
    public synchronized long totalSupply(String contractAddress) {
        return tokensByContract.getOrDefault(contractAddress, List.of()).size();
    }

    @Override
    public synchronized List<NftAmount> balance(String address) {
        return List.of(new NftAmount("20000000000", "DMC"));
    }

    private NftTransactionState record(String factHash, NftMintRequest request) {
        List<NftToken> tokens = tokensByContract.computeIfAbsent(request.contractAddress(), key -> new ArrayList<>());
        long index = tokens.size();
        tokens.add(new NftToken(request.contractAddress(), index, request.receiver(), request.uri(),
            request.hash(), true));
        indexesByFact.put(factHash, List.of(index));
        NftTransactionState confirmed = NftTransactionState.confirmed(height++, clock);
        transactions.put(factHash, confirmed);
        return confirmed;
    }
}
