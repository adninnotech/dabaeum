package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.LegacySignKeyBackfillService.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class LegacySignKeyBackfillRunnerTest {

    static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private final LegacySignKeyBackfillService service = mock(LegacySignKeyBackfillService.class);
    private final LegacySignKeyBackfillRunner runner = new LegacySignKeyBackfillRunner(service,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 기동_시_한_번_백필을_부르고_결과_요약을_돌려준다() {
        when(service.backfill(NOW)).thenReturn(List.of(
            new Result(UUID.randomUUID(), true, true, 3, 1),
            new Result(UUID.randomUUID(), false, false, 0, 0)));

        LegacySignKeyBackfillRunner.Summary summary = runner.runAndSummarize();

        verify(service).backfill(NOW);
        assertThat(summary.institutions()).isEqualTo(2);
        assertThat(summary.mastersCreated()).isEqualTo(1);
        assertThat(summary.versionsCreated()).isEqualTo(1);
        assertThat(summary.credentialsLinked()).isEqualTo(3);
        assertThat(summary.statusListsLinked()).isEqualTo(1);
    }

    @Test
    void 백필_예외는_기동을_막지_않는다() {
        when(service.backfill(any())).thenThrow(new IllegalStateException("platform public key is not configured"));
        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
