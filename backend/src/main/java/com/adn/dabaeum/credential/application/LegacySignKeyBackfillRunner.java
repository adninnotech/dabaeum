package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.LegacySignKeyBackfillService.Result;
import com.adn.dabaeum.credential.config.LegacyBackfillProperties;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 전환 이전 발급분 백필 진입점 (§11.3, 계획 7 결정 5). dabaeum.credential-proof.legacy-backfill.enabled=true 로
 * 기동하면 한 번 돌고 요약만 로그에 남긴다(기관 ID·키 자료 없음). 멱등이라 두 번 돌아도 무해하지만 전환이
 * 끝나면 플래그를 끄고 이 클래스를 지운다. 예외는 기동을 막지 않는다 — 재시도는 재기동이다.
 */
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "dabaeum.credential-proof.legacy-backfill", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LegacyBackfillProperties.class)
public class LegacySignKeyBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySignKeyBackfillRunner.class);

    public record Summary(int institutions, int mastersCreated, int versionsCreated, int credentialsLinked,
        int statusListsLinked) {
    }

    private final LegacySignKeyBackfillService service;
    private final Clock clock;

    public LegacySignKeyBackfillRunner(LegacySignKeyBackfillService service, Clock clock) {
        this.service = Objects.requireNonNull(service, "service");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Summary summary = runAndSummarize();
            log.info("LEGACY_SIGN_KEY_BACKFILL_DONE institutions={} mastersCreated={} versionsCreated={} "
                + "credentialsLinked={} statusListsLinked={}", summary.institutions(), summary.mastersCreated(),
                summary.versionsCreated(), summary.credentialsLinked(), summary.statusListsLinked());
        } catch (RuntimeException exception) {
            log.error("LEGACY_SIGN_KEY_BACKFILL_FAILED type={}", exception.getClass().getName(), exception);
        }
    }

    Summary runAndSummarize() {
        List<Result> results = service.backfill(clock.instant());
        return new Summary(results.size(),
            (int) results.stream().filter(Result::masterCreated).count(),
            (int) results.stream().filter(Result::versionCreated).count(),
            results.stream().mapToInt(Result::credentialsLinked).sum(),
            results.stream().mapToInt(Result::statusListsLinked).sum());
    }
}
