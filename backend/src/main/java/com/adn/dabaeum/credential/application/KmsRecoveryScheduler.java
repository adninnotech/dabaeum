package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.config.KmsRecoveryProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 첫 사이클(initial-delay)이 §15.1 의 기동 시 인계를 대신한다. 사이클 실패는 로그로만 남긴다. */
public final class KmsRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(KmsRecoveryScheduler.class);

    private final KmsRecoveryWorker worker;
    private final KmsRecoveryProperties properties;

    public KmsRecoveryScheduler(KmsRecoveryWorker worker, KmsRecoveryProperties properties) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Scheduled(initialDelayString = "${dabaeum.kms.recovery.initial-delay:10s}",
        fixedDelayString = "${dabaeum.kms.recovery.fixed-delay:5m}")
    public void runOnce() {
        try {
            worker.runOnce(properties.batchSize());
        } catch (RuntimeException exception) {
            log.warn("KMS_RECOVERY_CYCLE_FAILED type={}", exception.getClass().getName(), exception);
        }
    }
}
