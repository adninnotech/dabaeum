package com.adn.dabaeum.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.recommendation.application.CourseRecommendationService;
import com.adn.dabaeum.recommendation.application.RecommendationResult;
import com.adn.dabaeum.recommendation.domain.CandidateFilter;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationRepository;
import com.adn.dabaeum.recommendation.domain.RecommendationTopFactor;
import com.adn.dabaeum.recommendation.domain.ScoredCourse;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/specs/course-recommendation.md §12.5·§12.6 — 야간 배치가 쓴 점수 행을 API 가 어떻게 쓰는지 실 DB 로 확인한다.
 * 점수 행은 §3.4 제외 규칙을 그대로 거치고, 점수 순으로 MODEL 항목이 먼저 나오며, 모자라면 규칙 기반이 채운다.
 * 노출 로그에 MODEL·MODEL_SCORE 가 들어가는 것(V55 가 넓힌 CHECK)도 여기서 걸린다.
 */
class RecommendationModelScoreIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final String BADMINTON = "TSIX_040102";
    private static final String LEISURE = "TSIX_040101";
    private static final String HUMANITIES = "TSIX_050100";

    @Autowired
    RecommendationRepository repository;

    @Autowired
    CourseRecommendationService service;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    void 점수_행은_점수_순이고_제외_규칙과_반복_제목_정리를_그대로_거친다() {
        UUID institution = institution();
        UUID user = user();
        UUID run = run();
        UUID best = course(institution, "점수 1위", HUMANITIES, "RECRUITING");
        UUID second = course(institution, "점수 2위", HUMANITIES, "RECRUITING");
        UUID applied = course(institution, "이미 신청", HUMANITIES, "RECRUITING");
        UUID interested = course(institution, "이미 찜", HUMANITIES, "RECRUITING");
        UUID closed = course(institution, "모집 끝", HUMANITIES, "COMPLETED");
        UUID excluded = course(institution, "인기 섹션", HUMANITIES, "RECRUITING");
        UUID repeatLater = course(institution, "반복 제목", HUMANITIES, "RECRUITING");
        UUID repeatSooner = course(institution, "반복 제목", HUMANITIES, "RECRUITING");
        jdbcTemplate.update("UPDATE tb_courses SET recruit_end_date = ? WHERE id = ?", today.plusDays(9), repeatLater);
        jdbcTemplate.update("UPDATE tb_courses SET recruit_end_date = ? WHERE id = ?", today.plusDays(2), repeatSooner);
        enrollment(user, applied);
        interest(user, interested);
        score(run, user, best, "0.91000", "CLASSIFICATION");
        score(run, user, second, "0.75000", "REGION");
        score(run, user, applied, "0.99000", "CLASSIFICATION");
        score(run, user, interested, "0.98000", "CLASSIFICATION");
        score(run, user, closed, "0.97000", "CLASSIFICATION");
        score(run, user, excluded, "0.96000", "POPULARITY");
        score(run, user, repeatLater, "0.80000", "SCHEDULE");
        score(run, user, repeatSooner, "0.60000", "SCHEDULE");

        List<ScoredCourse> scored = repository.findModelScored(user,
            new CandidateFilter(user, List.of(excluded), today, false), 10);

        // 반복 제목은 §3.4 대로 먼저 마감되는 쪽(repeatSooner, 0.60)만 남고, 그 행의 점수로 정렬된다.
        assertThat(scored).extracting(ScoredCourse::courseId).containsExactly(best, second, repeatSooner);
        assertThat(scored.get(0).score()).isEqualByComparingTo(new BigDecimal("0.91000"));
        assertThat(scored.get(0).topFactor()).isEqualTo(RecommendationTopFactor.CLASSIFICATION);
        // 재학습 전의 옛 점수 행(REGION)도 읽을 수 있어야 한다.
        assertThat(scored.get(1).topFactor()).isEqualTo(RecommendationTopFactor.POPULARITY);
        // exclude 를 비우면 인기 섹션 강좌(0.96)도 후보로 돌아오고, limit 2 에서 끊긴다.
        assertThat(repository.findModelScored(user, new CandidateFilter(user, List.of(), today, false), 2))
            .extracting(ScoredCourse::courseId).containsExactly(excluded, best);
    }

    @Test
    void 점수_행이_있는_학습자는_MODEL로_시작하고_모자라면_규칙_기반이_채우며_노출로그에_MODEL이_남는다() {
        UUID institution = institution();
        UUID user = user();
        UUID run = run();
        UUID reference = course(institution, "배드민턴 입문", BADMINTON, "RECRUITING");
        UUID similar = course(institution, "탁구 교실", LEISURE, "RECRUITING");
        UUID scoredCourse = course(institution, "모델 추천", HUMANITIES, "RECRUITING");
        interest(user, reference);
        score(run, user, scoredCourse, "0.88000", "AGE_BAND");

        RecommendationResult result = service.recommend(Optional.of(learner(user)), List.of(), 20);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.MODEL);
        List<UUID> ids = result.items().stream().map(item -> item.course().course().id()).toList();
        assertThat(ids.get(0)).isEqualTo(scoredCourse);
        assertThat(ids).contains(similar).doesNotHaveDuplicates().doesNotContain(reference);
        assertThat(result.items().get(0).basis()).isEqualTo(RecommendationBasis.MODEL);
        assertThat(result.items().get(0).matchLevel()).isZero();
        assertThat(result.items().get(0).reason().type()).isEqualTo(RecommendationReasonType.MODEL_SCORE);
        assertThat(result.items().get(0).reason().score()).isEqualByComparingTo(new BigDecimal("0.88000"));
        assertThat(result.items().get(0).reason().topFactor()).isEqualTo(RecommendationTopFactor.AGE_BAND);
        assertThat(result.items().stream().filter(item -> item.course().course().id().equals(similar)).findFirst()
            .orElseThrow().basis()).isEqualTo(RecommendationBasis.PERSONAL);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT basis || '/' || reason_type FROM tb_recommendation_impressions WHERE user_id = ? AND position = 1",
            String.class, user)).isEqualTo("MODEL/MODEL_SCORE");
    }

    @Test
    void 점수_행이_없으면_모델_경로는_빈_목록이다() {
        UUID user = user();

        assertThat(repository.findModelScored(user, new CandidateFilter(user, List.of(), today, false), 10)).isEmpty();
    }

    private static AuthenticatedUserContext learner(UUID userId) {
        return new AuthenticatedUserContext(userId, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private UUID run() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_recommendation_model_runs (id, started_at, finished_at, result, model_type) "
                + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TRAINED', 'XGBOOST')", id);
        return id;
    }

    private void score(UUID run, UUID user, UUID course, String score, String topFactor) {
        jdbcTemplate.update(
            "INSERT INTO tb_course_recommendation_scores (user_id, course_id, score, top_factor, run_id, computed_at) "
                + "VALUES (?, ?, ?::numeric, ?, ?, CURRENT_TIMESTAMP)",
            user, course, score, topFactor, run);
    }

    private UUID institution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, uniqueCode("inst"), uniqueCode("inst-name"));
        return id;
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)", id, code, code + "@example.test");
        return id;
    }

    private UUID course(UUID institution, String title, String classificationCode, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type, classification_code, status,
                    start_date, end_date, recruit_start_date, recruit_end_date, capacity
                ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, ?, ?, ?, ?, 20)
                """,
            id, institution, uniqueCode("course"), title, classificationCode, status,
            today.plusDays(7), today.plusDays(60), today.minusDays(1), today.plusDays(5));
        return id;
    }

    private void interest(UUID user, UUID course) {
        jdbcTemplate.update(
            "INSERT INTO tb_course_interests (id, user_id, course_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
            UUID.randomUUID(), user, course);
    }

    private void enrollment(UUID user, UUID course) {
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, 'APPROVED')",
            UUID.randomUUID(), course, user);
    }
}
