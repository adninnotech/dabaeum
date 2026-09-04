package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.fabric.config.FabricWorkerProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class CredentialFabricScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        CredentialFabricScheduler.class);

    private final CredentialFabricWorker worker;
    private final CredentialFabricReconciler reconciler;
    private final FabricWorkerProperties properties;
    private final Clock clock;

    public CredentialFabricScheduler(
        CredentialFabricWorker worker,
        CredentialFabricReconciler reconciler,
        FabricWorkerProperties properties,
        Clock clock
    ) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${dabaeum.fabric.worker.fixed-delay:5s}")
    public void runOnce() {
        Instant now = clock.instant();
        try {
            worker.processBatch(properties.batchSize(), now);
        } catch (RuntimeException exception) {
            warn("FABRIC_WORKER_CYCLE_FAILED", exception);
        }
        try {
            reconciler.reconcileBatch(properties.batchSize(), now);
        } catch (RuntimeException exception) {
            warn("FABRIC_RECONCILER_CYCLE_FAILED", exception);
        }
    }

    private void warn(String code, RuntimeException exception) {
        // 코드와 예외 타입만으로는 사이클 실패 원인을 알 수 없다.
        // 원인은 서버 로그에만 남기고 외부로는 아무것도 내보내지 않는다.
        LOGGER.warn("Credential Fabric scheduler failure: code={}, type={}",
            code, exception.getClass().getName(), exception);
    }
}
