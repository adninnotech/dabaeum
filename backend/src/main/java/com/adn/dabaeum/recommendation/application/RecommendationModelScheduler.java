package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import com.adn.dabaeum.recommendation.application.ModelProcessRunner.ProcessResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 추천 모델 학습 배치 (docs/specs/course-recommendation.md §12.2). 매일 한 서버만 Python 학습 스크립트를 돌린다(advisory lock).
 * DB 접속정보는 환경변수로만 넘기고 명령·로그에 쓰지 않는다. 실패해도 API 는 규칙 기반으로 그대로 돈다.
 */
public final class RecommendationModelScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationModelScheduler.class);
    /** run.py 종료 코드 규약 (§12.2). */
    private static final int EXIT_TRAINED = 0;
    /** 표본 부족·규칙보다 나쁨 둘 다 3 이다. 어느 쪽인지는 마지막 줄 JSON 의 result 로 가른다. */
    private static final int EXIT_SKIPPED = 3;
    private static final Pattern SUMMARY_RESULT = Pattern.compile("\"result\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final String WORSE_THAN_RULES = "SKIPPED_WORSE_THAN_RULES";

    /** 한 번 실행한 결과. 테스트가 로그를 잡지 않고 결과를 확인하려고 돌려준다. */
    enum Outcome {
        TRAINED, SKIPPED_INSUFFICIENT_DATA, SKIPPED_WORSE_THAN_RULES, SKIPPED_LOCKED,
        FAILED_TIMEOUT, FAILED_EXIT, FAILED_EXCEPTION
    }

    /** {@code SingleInstanceJobRunner#runExclusively} 와 같은 모양. final 클래스라 테스트용으로 함수형 인터페이스로 받는다. */
    @FunctionalInterface
    interface ExclusiveJobRunner {
        boolean runExclusively(long key, Runnable job);
    }

    private final RecommendationModelProperties properties;
    private final PopularityProperties popularity;
    private final DatabaseCredentials credentials;
    private final ModelProcessRunner processes;
    private final ExclusiveJobRunner runner;

    RecommendationModelScheduler(
        RecommendationModelProperties properties,
        PopularityProperties popularity,
        DatabaseCredentials credentials,
        ModelProcessRunner processes,
        ExclusiveJobRunner runner
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.popularity = Objects.requireNonNull(popularity, "popularity");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Scheduled(cron = "${dabaeum.recommendation.model.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void runOnce() {
        execute();
    }

    Outcome execute() {
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        try {
            boolean ran = runner.runExclusively(AdvisoryLockKeys.RECOMMENDATION_MODEL_TRAINING, () -> outcome.set(train()));
            if (!ran) {
                LOGGER.info("RECOMMENDATION_MODEL_SKIPPED reason=locked");
                return Outcome.SKIPPED_LOCKED;
            }
            return outcome.get();
        } catch (RuntimeException exception) {
            LOGGER.warn("RECOMMENDATION_MODEL_FAILED type={}", exception.getClass().getName(), exception);
            return Outcome.FAILED_EXCEPTION;
        }
    }

    private Outcome train() {
        Map<String, String> environment = environment();
        ProcessResult result = processes.run(command(), environment, properties.timeout());
        return report(result);
    }

    private static Outcome report(ProcessResult result) {
        if (result.timedOut()) {
            LOGGER.warn("RECOMMENDATION_MODEL_FAILED reason=timeout");
            return Outcome.FAILED_TIMEOUT;
        }
        return switch (result.exitCode()) {
            case EXIT_TRAINED -> {
                LOGGER.info("RECOMMENDATION_MODEL_TRAINED summary={}", result.lastStdoutLine());
                yield Outcome.TRAINED;
            }
            case EXIT_SKIPPED -> {
                LOGGER.info("RECOMMENDATION_MODEL_SKIPPED summary={}", result.lastStdoutLine());
                yield skippedOutcome(result.lastStdoutLine());
            }
            default -> {
                LOGGER.warn("RECOMMENDATION_MODEL_FAILED exit={}", result.exitCode());
                yield Outcome.FAILED_EXIT;
            }
        };
    }

    private static Outcome skippedOutcome(String summary) {
        Matcher matcher = summary == null ? null : SUMMARY_RESULT.matcher(summary);
        boolean worse = matcher != null && matcher.find() && WORSE_THAN_RULES.equals(matcher.group(1));
        return worse ? Outcome.SKIPPED_WORSE_THAN_RULES : Outcome.SKIPPED_INSUFFICIENT_DATA;
    }

    /** 인기 집계 창(dabaeum.popularity.window-days)을 함께 넘겨 모델의 인기 feature 와 규칙 기준선이 API 와 같은 정의를 쓰게 한다. */
    private List<String> command() {
        return List.of(
            properties.python(), properties.script(),
            "--min-positive", String.valueOf(properties.minPositiveSamples()),
            "--top-n", String.valueOf(properties.topNPerUser()),
            "--popularity-window-days", String.valueOf(popularity.windowDays()),
            "--report-dir", properties.reportDir(),
            "--model-dir", properties.modelDir());
    }

    private Map<String, String> environment() {
        DatabaseTarget target = DatabaseTarget.fromJdbcUrl(credentials.url());
        return Map.of(
            "DABAEUM_DB_HOST", target.host(),
            "DABAEUM_DB_PORT", target.port(),
            "DABAEUM_DB_NAME", target.database(),
            "DABAEUM_DB_USERNAME", Objects.requireNonNullElse(credentials.username(), ""),
            "DABAEUM_DB_PASSWORD", Objects.requireNonNullElse(credentials.password(), ""));
    }

    /** spring.datasource 의 url·username·password. toString 에 비밀번호를 쓰지 않는다. */
    record DatabaseCredentials(String url, String username, String password) {

        @Override
        public String toString() {
            return "DatabaseCredentials[url=" + url + ", username=" + username + ", password=****]";
        }
    }

    /** {@code jdbc:postgresql://host[:port]/db[?...]} 에서 뽑은 접속 대상. 포트가 없으면 5432. */
    record DatabaseTarget(String host, String port, String database) {

        private static final String DEFAULT_PORT = "5432";
        private static final Pattern JDBC_URL =
            Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^/?]+)(?:\\?.*)?$");

        static DatabaseTarget fromJdbcUrl(String url) {
            Matcher matcher = url == null ? null : JDBC_URL.matcher(url);
            if (matcher == null || !matcher.matches()) {
                // URL 에 비밀값이 들어 있을 수 있어 메시지에 넣지 않는다.
                throw new IllegalArgumentException("spring.datasource.url is not jdbc:postgresql://host[:port]/db");
            }
            String port = matcher.group(2) == null ? DEFAULT_PORT : matcher.group(2);
            return new DatabaseTarget(matcher.group(1), port, matcher.group(3));
        }
    }
}
