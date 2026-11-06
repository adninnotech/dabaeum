package com.adn.dabaeum.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import com.adn.dabaeum.recommendation.application.CourseRecommendationService;
import com.adn.dabaeum.recommendation.application.RecommendationResult;
import com.adn.dabaeum.recommendation.application.RecommendedCourse;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.ReferenceSignal;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/specs/course-recommendation.md §3·§4·§6·§7 — 서비스가 실 저장소(MyBatis SQL)와 붙어
 * PERSONAL → 연령대 → 지역 → 전체 인기 순으로 채우고, 항목별 basis·reason 과 노출 로그를 만드는지 확인한다.
 *
 * <p>dev DB 에는 다른 강좌·인기 집계가 함께 있으므로 폴백 픽스처는 아주 큰 점수를 주고, 단언은 픽스처 강좌의
 * 상대 순서와 속성으로 한다. 연령대 '10'(만 19세 이하)은 dev 데이터가 적은 세그먼트다. 지역은 추천 검증용 가상
 * 데이터가 '99'(기타, 지역 없음 취급)를 뺀 모든 구·군을 쓰므로 겹침을 피할 수 없어, 지역 단계가 칸을 다 채우는 경우를
 * 단언에서 허용한다.
 */
class CourseRecommendationServiceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    // 기준·유사 강좌는 학력보완 > 초등학력보완 아래 두 소분류로 둔다. 추천 검증용 가상 데이터(ml/recommend/synthetic.py)는
    // 학력보완(TSIX_02)을 쓰지 않아, dev 강좌가 유사도 단계의 20칸을 먼저 채우지 않는다.
    private static final String REFERENCE_CLASS = "TSIX_020101"; // 학력보완 > 초등학력보완 > 유아초등대상 연계교육
    private static final String SIBLING_CLASS = "TSIX_020102";   // 같은 중분류, 다른 소분류 → matchLevel 2
    private static final String HUMANITIES = "TSIX_050100"; // 다른 대분류 → 유사도 후보 아님
    private static final int HUGE = 1_000_000;

    @Autowired
    CourseRecommendationService service;

    @Autowired
    PopularityProperties popularity;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    void 기준강좌가_있으면_PERSONAL로_시작하고_모자라면_관심분야_연령대_전체_순으로_채우며_이유와_노출로그를_남긴다() {
        UUID institution = institution();
        UUID user = user(today.minusYears(15)); // 연령대 '10', 성인 아님
        UUID reference = course(institution, "초등 연계 입문", REFERENCE_CLASS);
        UUID similar = course(institution, "초등학력 인증 준비", SIBLING_CLASS);
        UUID byAge = course(institution, "10대 인기", HUMANITIES);
        String artsCode = jdbcTemplate.queryForObject(
            "SELECT code FROM tb_course_classifications WHERE level = 3 AND enabled AND code LIKE 'TSIX_04%' ORDER BY code LIMIT 1",
            String.class);
        UUID byInterest = course(institution, "관심 분야 강좌", artsCode);
        jdbcTemplate.update("INSERT INTO tb_user_interests (user_id, classification_code) VALUES (?, 'TSIX_040000')", user);
        UUID byAll = course(institution, "전체 인기", HUMANITIES);
        interest(user, reference);
        popularity(byAge, "AGE_BAND", "10", HUGE);
        popularity(byAge, "ALL", "*", HUGE + 1); // 연령대 단계에서 이미 뽑혔으므로 전체 단계에서 다시 나오면 안 된다
        popularity(byInterest, "ALL", "*", HUGE + 2);
        popularity(byAll, "ALL", "*", HUGE);

        RecommendationResult result = service.recommend(Optional.of(learner(user)), List.of(), 20);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.PERSONAL);
        List<UUID> ids = result.items().stream().map(item -> item.course().course().id()).toList();
        assertThat(ids).doesNotHaveDuplicates().doesNotContain(reference);
        // dev 에 같은 소분류 강좌가 있으면 그쪽(matchLevel 3)이 앞설 수 있어, 첫 칸은 근거만 본다.
        assertThat(result.items().get(0).basis()).isEqualTo(RecommendationBasis.PERSONAL);
        // 칸을 채우는 단계는 PERSONAL → 관심 분야 → 연령대 → 전체 순으로만 내려간다.
        List<RecommendationBasis> stages = List.of(RecommendationBasis.PERSONAL, RecommendationBasis.INTEREST,
            RecommendationBasis.AGE_BAND, RecommendationBasis.POPULAR);
        assertThat(result.items()).extracting(item -> stages.indexOf(item.basis()))
            .doesNotContain(-1).isSorted();
        // dev 의 다른 강좌가 칸을 채울 수 있어 뒤 단계 픽스처는 나왔을 때 순서만 본다.
        List<UUID> fixtures = ids.stream().filter(Set.of(similar, byInterest, byAge, byAll)::contains).toList();
        assertThat(fixtures).startsWith(similar, byInterest);
        assertThat(fixtures).isSubsetOf(similar, byInterest, byAge, byAll);
        if (fixtures.contains(byAge) && fixtures.contains(byAll)) {
            assertThat(fixtures).containsExactly(similar, byInterest, byAge, byAll);
        }

        Map<UUID, RecommendedCourse> byId = result.items().stream()
            .collect(java.util.stream.Collectors.toMap(item -> item.course().course().id(), item -> item));
        RecommendedCourse personal = byId.get(similar);
        assertThat(personal.basis()).isEqualTo(RecommendationBasis.PERSONAL);
        assertThat(personal.matchLevel()).isEqualTo(2);
        assertThat(personal.reason().type()).isEqualTo(RecommendationReasonType.SIMILAR_CLASSIFICATION);
        assertThat(personal.reason().sourceCourseId()).isEqualTo(reference);
        assertThat(personal.reason().sourceSignal()).isEqualTo(ReferenceSignal.INTEREST);
        assertThat(personal.reason().classificationCode()).isEqualTo("TSIX_020100");

        assertThat(byId.get(byInterest).basis()).isEqualTo(RecommendationBasis.INTEREST);
        assertThat(byId.get(byInterest).reason().type()).isEqualTo(RecommendationReasonType.INTEREST);
        assertThat(byId.get(byInterest).reason().classificationCode()).isEqualTo("TSIX_040000");
        assertThat(byId.get(byInterest).reason().classificationName()).isEqualTo("문화예술교육");
        if (byId.containsKey(byAge)) {
            assertThat(byId.get(byAge).basis()).isEqualTo(RecommendationBasis.AGE_BAND);
            assertThat(byId.get(byAge).reason().ageBand()).isEqualTo("10");
        }
        if (byId.containsKey(byAll)) {
            assertThat(byId.get(byAll).basis()).isEqualTo(RecommendationBasis.POPULAR);
            assertThat(byId.get(byAll).reason().type()).isEqualTo(RecommendationReasonType.POPULAR);
        }

        assertThat(result.items()).allSatisfy(item -> assertThat(item.impressionId()).isNotNull());
        List<Integer> positions = jdbcTemplate.queryForList(
            "SELECT position FROM tb_recommendation_impressions WHERE user_id = ? ORDER BY position", Integer.class, user);
        assertThat(positions).hasSize(result.items().size()).isSorted().startsWith(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT basis FROM tb_recommendation_impressions WHERE user_id = ? AND position = 1", String.class, user))
            .isEqualTo("PERSONAL");
    }

    @Test
    void 신호_생년월일이_없는_학습자는_전체_인기만_타고_로그는_남긴다() {
        UUID institution = institution();
        UUID user = user(null);
        UUID byAll = course(institution, "전체 인기", HUMANITIES);
        popularity(byAll, "ALL", "*", HUGE);

        RecommendationResult result = service.recommend(Optional.of(learner(user)), List.of(), 20);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.POPULAR);
        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).course().course().id()).isEqualTo(byAll);
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.basis()).isEqualTo(RecommendationBasis.POPULAR);
            assertThat(item.reason().type()).isEqualTo(RecommendationReasonType.POPULAR);
            assertThat(item.impressionId()).isNotNull();
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_recommendation_impressions WHERE user_id = ?", Integer.class, user))
            .isEqualTo(result.items().size());
    }

    @Test
    void 비로그인은_exclude를_뺀_전체_인기를_받고_노출로그가_없다() {
        UUID institution = institution();
        UUID first = course(institution, "전체 인기 1", HUMANITIES);
        UUID second = course(institution, "전체 인기 2", HUMANITIES);
        popularity(first, "ALL", "*", HUGE + 1);
        popularity(second, "ALL", "*", HUGE);
        int before = impressionCount();

        RecommendationResult plain = service.recommend(Optional.empty(), List.of(), 20);
        RecommendationResult excluded = service.recommend(Optional.empty(), List.of(first), 20);

        assertThat(plain.basis()).isEqualTo(RecommendationBasis.POPULAR);
        assertThat(plain.items().get(0).course().course().id()).isEqualTo(first);
        assertThat(plain.items().get(1).course().course().id()).isEqualTo(second);
        assertThat(plain.items()).allSatisfy(item -> assertThat(item.impressionId()).isNull());
        assertThat(excluded.items().get(0).course().course().id()).isEqualTo(second);
        assertThat(excluded.items()).extracting(item -> item.course().course().id()).doesNotContain(first);
        assertThat(impressionCount()).isEqualTo(before);
    }

    private static AuthenticatedUserContext learner(UUID userId) {
        return new AuthenticatedUserContext(userId, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private int impressionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_recommendation_impressions", Integer.class);
    }

    private UUID institution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, uniqueCode("inst"), uniqueCode("inst-name"));
        return id;
    }

    private UUID user(LocalDate birthDate) {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("user");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email, birth_date) VALUES (?, ?, ?, ?)",
            id, code, code + "@example.test", birthDate);
        return id;
    }

    private UUID course(UUID institution, String title, String classificationCode) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type, classification_code, status,
                    start_date, end_date, recruit_start_date, recruit_end_date, capacity
                ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, 'RECRUITING', ?, ?, ?, ?, 20)
                """,
            id, institution, uniqueCode("course"), title, classificationCode,
            today.plusDays(7), today.plusDays(60), today.minusDays(1), today.plusDays(5));
        return id;
    }

    private void interest(UUID user, UUID course) {
        jdbcTemplate.update(
            "INSERT INTO tb_course_interests (id, user_id, course_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
            UUID.randomUUID(), user, course);
    }

    private void popularity(UUID course, String segment, String key, int score) {
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_popularity (
                    course_id, segment_type, segment_key, window_days, enrollment_count, view_count, score, computed_at
                ) VALUES (?, ?, ?, ?, 0, 0, ?, CURRENT_TIMESTAMP)
                """,
            course, segment, key, popularity.windowDays(), score);
    }
}
