package com.adn.dabaeum.credential.application;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 기관 서명키 생성 요청을 주기적으로 처리한다. 사이클 실패는 로그로만 남긴다. */
public final class InstitutionSigningKeyScheduler {

    private static final int BATCH_SIZE = 10;
    private static final Logger log = LoggerFactory.getLogger(InstitutionSigningKeyScheduler.class);

    private final InstitutionSigningKeyProvisioner provisioner;

    public InstitutionSigningKeyScheduler(InstitutionSigningKeyProvisioner provisioner) {
        this.provisioner = Objects.requireNonNull(provisioner, "provisioner");
    }

    @Scheduled(initialDelayString = "${dabaeum.kms.provisioning.initial-delay:15s}",
        fixedDelayString = "${dabaeum.kms.provisioning.fixed-delay:30s}")
    public void runOnce() {
        try {
            provisioner.runOnce(BATCH_SIZE);
        } catch (RuntimeException exception) {
            log.warn("INSTITUTION_SIGNING_KEY_CYCLE_FAILED type={}", exception.getClass().getName(), exception);
        }
    }
}
