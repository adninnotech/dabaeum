package com.adn.dabaeum.recommendation.application;

import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 추천 모델 학습 배치 설정 (docs/specs/course-recommendation.md §12.2). 기본은 꺼져 있다 — 서버에 Python·venv 를 준비한 뒤 켠다(§12.7).
 *
 * @param enabled 배치를 켤지
 * @param cron 실행 시각(Asia/Seoul)
 * @param python venv 의 python 실행 파일 경로
 * @param script {@code ml/recommend/run.py} 경로
 * @param timeout 이 시간을 넘기면 프로세스를 강제 종료한다
 * @param minPositiveSamples 학습에 필요한 최소 양성 표본 수(§12.4)
 * @param topNPerUser 회원별로 남길 점수 행 수
 * @param reportDir 연관도 리포트를 쓸 디렉터리
 * @param modelDir 모델 파일을 쓸 디렉터리
 */
@ConfigurationProperties(prefix = "dabaeum.recommendation.model")
public record RecommendationModelProperties(
    Boolean enabled,
    String cron,
    String python,
    String script,
    Duration timeout,
    Integer minPositiveSamples,
    Integer topNPerUser,
    String reportDir,
    String modelDir
) {

    private static final String DEFAULT_CRON = "0 0 3 * * *";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    private static final int DEFAULT_MIN_POSITIVE_SAMPLES = 2000;
    private static final int DEFAULT_TOP_N_PER_USER = 50;

    public RecommendationModelProperties {
        enabled = enabled != null && enabled;
        cron = cron == null ? DEFAULT_CRON : cron;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        minPositiveSamples = minPositiveSamples == null ? DEFAULT_MIN_POSITIVE_SAMPLES : minPositiveSamples;
        topNPerUser = topNPerUser == null ? DEFAULT_TOP_N_PER_USER : topNPerUser;
        if (cron.isBlank() || timeout.isNegative() || timeout.isZero() || minPositiveSamples < 1 || topNPerUser < 1) {
            throw new IllegalArgumentException("recommendation model settings out of range");
        }
        if (enabled && Stream.of(python, script, reportDir, modelDir).anyMatch(RecommendationModelProperties::isBlank)) {
            throw new IllegalArgumentException("recommendation model paths are required when enabled");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
