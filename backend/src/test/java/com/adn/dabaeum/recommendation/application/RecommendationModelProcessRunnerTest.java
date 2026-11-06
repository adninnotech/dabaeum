package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.recommendation.application.ModelProcessRunner.ProcessResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** 기본 프로세스 실행기 — 종료 코드, stdout 마지막 줄, 환경변수 전달, 시간 초과 시 강제 종료. */
@EnabledOnOs({OS.LINUX, OS.MAC})
class RecommendationModelProcessRunnerTest {

    private final DefaultModelProcessRunner runner = new DefaultModelProcessRunner();

    @Test
    void 종료_코드와_마지막_줄을_돌려준다() {
        ProcessResult result = runner.run(
            List.of("/bin/sh", "-c", "echo first; echo err 1>&2; echo \"{\\\"v\\\":$MODEL_TEST}\"; exit 3"),
            Map.of("MODEL_TEST", "7"), Duration.ofSeconds(10));

        assertThat(result).isEqualTo(new ProcessResult(3, "{\"v\":7}", false));
    }

    @Test
    void 시간_초과면_강제_종료하고_표시한다() {
        long started = System.nanoTime();

        ProcessResult result = runner.run(List.of("/bin/sh", "-c", "sleep 30"), Map.of(), Duration.ofMillis(300));

        assertThat(result.timedOut()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
    }
}
