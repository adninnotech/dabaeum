package com.adn.dabaeum.nft.application;

import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 대기 기록 → 발행 → 확정 순서로 한 바퀴 돈다. 한 단계가 실패해도 다음 단계는 돈다. */
public final class NftIssuanceScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NftIssuanceScheduler.class);

    private final NftIssuanceEnqueuer enqueuer;
    private final NftIssuanceWorker worker;
    private final NftIssuanceReconciler reconciler;
    private final int batchSize;
    private final Clock clock;

    public NftIssuanceScheduler(NftIssuanceEnqueuer enqueuer, NftIssuanceWorker worker,
        NftIssuanceReconciler reconciler, int batchSize, Clock clock) {
        this.enqueuer = Objects.requireNonNull(enqueuer, "enqueuer");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.batchSize = batchSize;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(initialDelayString = "${dabaeum.nft.worker.initial-delay:30s}",
        fixedDelayString = "${dabaeum.nft.worker.fixed-delay:10s}")
    public void runOnce() {
        run("NFT_ENQUEUE_CYCLE_FAILED", () -> enqueuer.enqueue(clock.instant(), batchSize));
        run("NFT_WORKER_CYCLE_FAILED", () -> worker.processBatch(batchSize, clock.instant()));
        run("NFT_RECONCILER_CYCLE_FAILED", () -> reconciler.reconcileBatch(batchSize, clock.instant()));
    }

    private static void run(String code, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("NFT scheduler failure: code={}, type={}", code, exception.getClass().getName(), exception);
        }
    }
}
