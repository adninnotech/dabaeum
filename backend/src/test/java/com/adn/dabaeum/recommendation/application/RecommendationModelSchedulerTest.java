package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import com.adn.dabaeum.recommendation.application.ModelProcessRunner.ProcessResult;
import com.adn.dabaeum.recommendation.application.RecommendationModelScheduler.DatabaseCredentials;
import com.adn.dabaeum.recommendation.application.RecommendationModelScheduler.Outcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** docs/specs/course-recommendation.md §12.2 — 명령·환경변수 구성과 종료 코드 → 결과 매핑. */
class RecommendationModelSchedulerTest {

    private static final String PASSWORD = "s3cret-pw";
    private static final RecommendationModelProperties PROPERTIES = new RecommendationModelProperties(
        true, null, "/venv/bin/python", "/ml/recommend/run.py", Duration.ofMinutes(7), 2000, 50, "/ml/reports", "/ml/models");
    private static final PopularityProperties POPULARITY = new PopularityProperties(14, 3, 1);
    private static final DatabaseCredentials CREDENTIALS =
        new DatabaseCredentials("jdbc:postgresql://127.0.0.1:15432/dabaeum_dev", "dabaeum", PASSWORD);

    private final FakeProcessRunner processes = new FakeProcessRunner();
    private final FakeLock lock = new FakeLock(true);

    private RecommendationModelScheduler scheduler() {
        return new RecommendationModelScheduler(PROPERTIES, POPULARITY, CREDENTIALS, processes, lock);
    }

    @Test
    void 명령은_설정값으로_만들고_비밀번호는_넣지_않는다() {
        processes.result = new ProcessResult(0, "{}", false);

        scheduler().execute();

        assertThat(processes.command).containsExactly(
            "/venv/bin/python", "/ml/recommend/run.py",
            "--min-positive", "2000", "--top-n", "50", "--popularity-window-days", "14",
            "--report-dir", "/ml/reports", "--model-dir", "/ml/models");
        assertThat(String.join(" ", processes.command)).doesNotContain(PASSWORD);
        assertThat(processes.timeout).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    void DB_접속정보는_환경변수로만_넘긴다() {
        processes.result = new ProcessResult(0, "{}", false);

        scheduler().execute();

        assertThat(processes.environment).containsExactlyInAnyOrderEntriesOf(Map.of(
            "DABAEUM_DB_HOST", "127.0.0.1",
            "DABAEUM_DB_PORT", "15432",
            "DABAEUM_DB_NAME", "dabaeum_dev",
            "DABAEUM_DB_USERNAME", "dabaeum",
            "DABAEUM_DB_PASSWORD", PASSWORD));
    }

    @Test
    void 잠금은_모델_학습_키로_잡는다() {
        processes.result = new ProcessResult(0, "{}", false);

        scheduler().execute();

        assertThat(lock.keys).containsExactly(AdvisoryLockKeys.RECOMMENDATION_MODEL_TRAINING);
    }

    @Test
    void 종료_코드_0_은_학습_완료다() {
        processes.result = new ProcessResult(0, "{\"auc\":0.71}", false);

        assertThat(scheduler().execute()).isEqualTo(Outcome.TRAINED);
    }

    @Test
    void 종료_코드_3_은_표본_부족_건너뜀이다() {
        processes.result = new ProcessResult(3, "{\"result\":\"SKIPPED_INSUFFICIENT_DATA\"}", false);

        assertThat(scheduler().execute()).isEqualTo(Outcome.SKIPPED_INSUFFICIENT_DATA);
    }

    @Test
    void 종료_코드_3_이라도_JSON_result_가_규칙보다_나쁨이면_그렇게_기록한다() {
        processes.result = new ProcessResult(3, "{\"result\": \"SKIPPED_WORSE_THAN_RULES\", \"auc\": 0.52}", false);

        assertThat(scheduler().execute()).isEqualTo(Outcome.SKIPPED_WORSE_THAN_RULES);
    }

    @Test
    void 종료_코드_3_에_JSON_이_없으면_표본_부족으로_본다() {
        processes.result = new ProcessResult(3, null, false);

        assertThat(scheduler().execute()).isEqualTo(Outcome.SKIPPED_INSUFFICIENT_DATA);
    }

    @Test
    void 그_밖의_종료_코드는_실패다() {
        processes.result = new ProcessResult(1, "Traceback", false);

        assertThat(scheduler().execute()).isEqualTo(Outcome.FAILED_EXIT);
    }

    @Test
    void 시간_초과는_종료_코드와_상관없이_실패다() {
        processes.result = new ProcessResult(0, null, true);

        assertThat(scheduler().execute()).isEqualTo(Outcome.FAILED_TIMEOUT);
    }

    @Test
    void 다른_서버가_잠금을_쥐고_있으면_실행하지_않는다() {
        FakeLock busy = new FakeLock(false);

        Outcome outcome = new RecommendationModelScheduler(PROPERTIES, POPULARITY, CREDENTIALS, processes, busy).execute();

        assertThat(outcome).isEqualTo(Outcome.SKIPPED_LOCKED);
        assertThat(processes.command).isNull();
    }

    @Test
    void 실행기_예외는_삼키고_실패로_남긴다() {
        processes.failure = new IllegalStateException("boom");

        assertThat(scheduler().execute()).isEqualTo(Outcome.FAILED_EXCEPTION);
    }

    @Test
    void JDBC_URL_이_잘못되면_실행하지_않고_실패로_남긴다() {
        DatabaseCredentials broken = new DatabaseCredentials("jdbc:mysql://h/db", "u", PASSWORD);

        Outcome outcome = new RecommendationModelScheduler(PROPERTIES, POPULARITY, broken, processes, lock).execute();

        assertThat(outcome).isEqualTo(Outcome.FAILED_EXCEPTION);
        assertThat(processes.command).isNull();
    }

    @Test
    void 접속정보_toString_에_비밀번호가_없다() {
        assertThat(CREDENTIALS.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void runOnce_는_예외를_밖으로_던지지_않는다() {
        processes.failure = new IllegalStateException("boom");

        scheduler().runOnce();

        assertThat(lock.keys).hasSize(1);
    }

    private static final class FakeProcessRunner implements ModelProcessRunner {
        private ProcessResult result = new ProcessResult(0, null, false);
        private RuntimeException failure;
        private List<String> command;
        private Map<String, String> environment;
        private Duration timeout;

        @Override
        public ProcessResult run(List<String> command, Map<String, String> environment, Duration timeout) {
            this.command = command;
            this.environment = environment;
            this.timeout = timeout;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeLock implements RecommendationModelScheduler.ExclusiveJobRunner {
        private final boolean acquired;
        private final List<Long> keys = new ArrayList<>();

        private FakeLock(boolean acquired) {
            this.acquired = acquired;
        }

        @Override
        public boolean runExclusively(long key, Runnable job) {
            keys.add(key);
            if (acquired) {
                job.run();
            }
            return acquired;
        }
    }
}
