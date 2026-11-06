package com.adn.dabaeum.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.popularity.domain.PopularitySegment;
import com.adn.dabaeum.recommendation.domain.CandidateFilter;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import com.adn.dabaeum.recommendation.domain.RecommendationImpression;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationRepository;
import com.adn.dabaeum.recommendation.domain.ReferenceCourse;
import com.adn.dabaeum.recommendation.domain.ReferenceSignal;
import com.adn.dabaeum.recommendation.domain.SimilarCourse;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** docs/specs/course-recommendation.md §3·§4·§7 — 기준 강좌, 분류 유사도·정렬·제외, 인기 폴백, 노출 로그 SQL. */
class RecommendationMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final String BADMINTON = "TSIX_040102"; // 문화예술 > 레저생활스포츠 > 생활스포츠
    private static final String LEISURE = "TSIX_040101";   // 같은 중분류, 다른 소분류
    private static final String CRAFT = "TSIX_040201";     // 같은 대분류, 다른 중분류
    private static final String HUMANITIES = "TSIX_050100"; // 다른 대분류
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    // dev DB 에는 같은 분류의 강좌(추천 검증용 가상강좌 등)와 인기 집계가 함께 있다. 결과를 넉넉히 받아
    // 이 테스트가 만든 강좌만 남겨 순서·포함 여부를 본다. SQL 의 정렬은 전체 순서라 부분 순서도 그대로다.
    private static final int ALL = 100_000;

    @Autowired
    RecommendationRepository repository;

    private final Set<UUID> created = new HashSet<>();

    @Test
    void 기준강좌는_찜과_신청_수료만_세고_분류_없는_강좌는_뺀다() {
        UUID institution = institution();
        UUID user = user(null);
        UUID interested = course(institution, "찜한 강좌", BADMINTON, "RECRUITING");
        UUID applied = course(institution, "신청한 강좌", CRAFT, "RECRUITING");
        UUID withdrawn = course(institution, "철회한 강좌", HUMANITIES, "RECRUITING");
        UUID noCode = course(institution, "분류 없는 강좌", null, "RECRUITING");
        interest(user, interested);
        enrollment(user, applied, "APPROVED");
        enrollment(user, withdrawn, "WITHDRAWN");
        interest(user, noCode);

        List<ReferenceCourse> references = repository.findReferenceCourses(user);

        assertThat(references).extracting(ReferenceCourse::courseId).containsExactlyInAnyOrder(interested, applied);
        assertThat(references).filteredOn(reference -> reference.courseId().equals(applied))
            .singleElement().satisfies(reference -> {
                assertThat(reference.signal()).isEqualTo(ReferenceSignal.ENROLLMENT);
                assertThat(reference.classificationCode()).isEqualTo(CRAFT);
            });
    }

    @Test
    void 유사도는_소분류_중분류_대분류_순이고_기준강좌_자신과_다른_대분류는_빠진다() {
        UUID institution = institution();
        UUID user = user(null);
        UUID reference = course(institution, "배드민턴 입문", BADMINTON, "RECRUITING");
        interest(user, reference);
        UUID sameSub = course(institution, "탁구 입문", BADMINTON, "RECRUITING");
        UUID sameMid = course(institution, "등산 교실", LEISURE, "RECRUITING");
        UUID sameTop = course(institution, "도자기 공예", CRAFT, "RECRUITING");
        course(institution, "인문학 산책", HUMANITIES, "RECRUITING");

        List<SimilarCourse> similar = mine(repository.findSimilar(user, filter(user), 30, Instant.EPOCH, 0, ALL));

        assertThat(similar).extracting(SimilarCourse::courseId).containsExactly(sameSub, sameMid, sameTop);
        assertThat(similar).extracting(SimilarCourse::matchLevel).containsExactly(3, 2, 1);
        assertThat(similar.get(0).matchedClassificationName()).isEqualTo("생활스포츠");
        assertThat(similar.get(1).matchedClassificationCode()).isEqualTo("TSIX_040100");
        assertThat(similar.get(2).matchedClassificationName()).isEqualTo("문화예술교육");
        assertThat(similar.get(0).sourceCourseId()).isEqualTo(reference);
        assertThat(similar.get(0).sourceSignal()).isEqualTo(ReferenceSignal.INTEREST);
    }

    @Test
    void 같은_일치_수준이면_인기_점수와_신규_보정_순이다() {
        UUID institution = institution();
        UUID user = user(null);
        interest(user, course(institution, "배드민턴 입문", BADMINTON, "RECRUITING"));
        UUID popular = course(institution, "탁구 입문", BADMINTON, "RECRUITING");
        UUID fresh = course(institution, "요가 입문", BADMINTON, "RECRUITING");
        UUID plain = course(institution, "수영 입문", BADMINTON, "RECRUITING");
        popularity(popular, "ALL", "*", 30, 4);
        popularity(fresh, "ALL", "*", 30, 1);
        Instant freshSince = Instant.now().minus(1, ChronoUnit.HOURS);
        jdbcTemplate.update("UPDATE tb_courses SET created_at = ? WHERE id IN (?, ?)",
            java.sql.Timestamp.from(freshSince.minus(1, ChronoUnit.DAYS)), popular, plain);

        List<SimilarCourse> similar = mine(repository.findSimilar(user, filter(user), 30, freshSince, 5, ALL));

        assertThat(similar).extracting(SimilarCourse::courseId).containsExactly(fresh, popular, plain);
    }

    @Test
    void 제외_규칙_신청_찜_모집아님_정원초과_exclude_반복제목() {
        UUID institution = institution();
        UUID user = user(null);
        interest(user, course(institution, "배드민턴 입문", BADMINTON, "RECRUITING"));
        UUID kept = course(institution, "탁구 입문", BADMINTON, "RECRUITING");
        UUID alreadyApplied = course(institution, "요가 입문", BADMINTON, "RECRUITING");
        enrollment(user, alreadyApplied, "APPLIED");
        UUID alreadyInterested = course(institution, "수영 입문", BADMINTON, "RECRUITING");
        interest(user, alreadyInterested);
        UUID completed = course(institution, "배드민턴 심화", BADMINTON, "RECRUITING");
        UUID completedEnrollment = enrollment(user, completed, "WITHDRAWN");
        jdbcTemplate.update(
            "INSERT INTO tb_completions (id, enrollment_id, attendance_rate, completed_minutes, status) VALUES (?, ?, 100, 60, 'COMPLETED')",
            UUID.randomUUID(), completedEnrollment);
        course(institution, "볼링 입문", BADMINTON, "RECRUITMENT_CLOSED");
        UUID full = course(institution, "농구 입문", BADMINTON, "RECRUITING");
        jdbcTemplate.update("UPDATE tb_courses SET capacity = 1, waitlist_capacity = 0 WHERE id = ?", full);
        enrollment(user(null), full, "APPROVED");
        UUID excluded = course(institution, "축구 입문", BADMINTON, "RECRUITING");
        UUID repeatFirst = course(institution, "배드민턴 중급", BADMINTON, "RECRUITING");
        UUID repeatSecond = course(institution, "배드민턴 중급", BADMINTON, "RECRUITING");
        jdbcTemplate.update("UPDATE tb_courses SET recruit_end_date = ? WHERE id = ?", TODAY.plusDays(3), repeatFirst);
        jdbcTemplate.update("UPDATE tb_courses SET recruit_end_date = ? WHERE id = ?", TODAY.plusDays(9), repeatSecond);

        CandidateFilter filter = new CandidateFilter(user, List.of(excluded), TODAY, false);
        List<SimilarCourse> similar = mine(repository.findSimilar(user, filter, 30, Instant.EPOCH, 0, ALL));

        assertThat(similar).extracting(SimilarCourse::courseId).containsExactlyInAnyOrder(kept, repeatFirst);
    }

    @Test
    void 성인에게는_어린이_청소년_전용_강좌가_빠지고_누구나_포함이면_남는다() {
        UUID institution = institution();
        UUID user = user(LocalDate.of(1990, 1, 1));
        interest(user, course(institution, "배드민턴 입문", BADMINTON, "RECRUITING"));
        UUID kidsOnly = course(institution, "어린이 탁구", BADMINTON, "RECRUITING");
        jdbcTemplate.update("UPDATE tb_courses SET target_audiences = 'CHILDREN,YOUTH' WHERE id = ?", kidsOnly);
        UUID anyone = course(institution, "가족 탁구", BADMINTON, "RECRUITING");
        jdbcTemplate.update("UPDATE tb_courses SET target_audiences = 'CHILDREN,ANYONE' WHERE id = ?", anyone);

        List<SimilarCourse> adult = mine(repository.findSimilar(user,
            new CandidateFilter(user, List.of(), TODAY, true), 30, Instant.EPOCH, 0, ALL));
        List<SimilarCourse> unknownAge = mine(repository.findSimilar(user, filter(user), 30, Instant.EPOCH, 0, ALL));

        assertThat(adult).extracting(SimilarCourse::courseId).containsExactly(anyone);
        assertThat(unknownAge).extracting(SimilarCourse::courseId).containsExactlyInAnyOrder(kidsOnly, anyone);
    }

    @Test
    void 인기_폴백은_세그먼트_점수_순이고_제외_규칙을_같이_적용한다() {
        UUID institution = institution();
        UUID user = user(null);
        UUID top = course(institution, "인기 1위", HUMANITIES, "RECRUITING");
        UUID second = course(institution, "인기 2위", CRAFT, "RECRUITING");
        UUID repeatLater = course(institution, "인기 반복", CRAFT, "RECRUITING");
        UUID repeatSooner = course(institution, "인기 반복", CRAFT, "RECRUITING");
        jdbcTemplate.update("UPDATE tb_courses SET recruit_end_date = ? WHERE id = ?", TODAY.plusDays(9), repeatLater);
        jdbcTemplate.update("UPDATE tb_courses SET recruit_end_date = ? WHERE id = ?", TODAY.plusDays(2), repeatSooner);
        popularity(repeatLater, "AGE_BAND", "30", 30, 6);
        popularity(repeatSooner, "AGE_BAND", "30", 30, 3);
        UUID applied = course(institution, "이미 신청", HUMANITIES, "RECRUITING");
        UUID closed = course(institution, "모집 끝", HUMANITIES, "COMPLETED");
        popularity(top, "AGE_BAND", "30", 30, 9);
        popularity(second, "AGE_BAND", "30", 30, 5);
        popularity(applied, "AGE_BAND", "30", 30, 7);
        popularity(closed, "AGE_BAND", "30", 30, 8);
        enrollment(user, applied, "WAITLISTED");

        List<UUID> popular = mineIds(repository.findPopular(PopularitySegment.AGE_BAND, "30", filter(user), 30, ALL));
        List<UUID> anonymousOtherBand = mineIds(repository.findPopular(PopularitySegment.AGE_BAND, "40",
            new CandidateFilter(null, List.of(), TODAY, false), 30, ALL));

        assertThat(popular).containsExactly(top, second, repeatSooner);
        assertThat(anonymousOtherBand).isEmpty();
    }

    @Test
    void 노출_로그는_본인만_한_번_클릭할_수_있고_오래된_것은_지운다() {
        UUID institution = institution();
        UUID user = user(null);
        UUID other = user(null);
        UUID course = course(institution, "배드민턴 입문", BADMINTON, "RECRUITING");
        // dev DB 는 QA 가 쌓은 실제 노출 로그를 공유한다. 지금 시각을 쓰면 그 행들까지 삭제 대상으로 세므로
        // 실데이터가 있을 수 없는 과거 시각을 기준으로 잡는다(트랜잭션 롤백이라 실제로 지워지지는 않는다).
        Instant now = Instant.parse("2001-01-01T00:00:00Z");
        UUID impression = UUID.randomUUID();
        repository.saveImpressions(List.of(new RecommendationImpression(impression, user, course,
            RecommendationBasis.PERSONAL, RecommendationReasonType.SIMILAR_CLASSIFICATION, 3, 1, now)));

        assertThat(repository.markClicked(impression, other, now)).isFalse();
        assertThat(repository.markClicked(impression, user, now)).isTrue();
        assertThat(repository.markClicked(impression, user, now)).isFalse();
        assertThat(repository.deleteImpressionsBefore(now)).isZero();
        assertThat(repository.deleteImpressionsBefore(now.plusSeconds(1))).isEqualTo(1);
    }

    private List<SimilarCourse> mine(List<SimilarCourse> similar) {
        return similar.stream().filter(course -> created.contains(course.courseId())).toList();
    }

    private List<UUID> mineIds(List<UUID> courseIds) {
        return courseIds.stream().filter(created::contains).toList();
    }

    private static CandidateFilter filter(UUID user) {
        return new CandidateFilter(user, List.of(), TODAY, false);
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
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email, birth_date) VALUES (?, ?, ?, ?)",
            id, code, code + "@example.test", birthDate);
        return id;
    }

    private UUID course(UUID institution, String title, String classificationCode, String status) {
        UUID id = UUID.randomUUID();
        created.add(id);
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type, classification_code, status,
                    start_date, end_date, recruit_start_date, recruit_end_date, capacity
                ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, ?, ?, ?, ?, 20)
                """,
            id, institution, uniqueCode("course"), title, classificationCode, status,
            TODAY.plusDays(7), TODAY.plusDays(60), TODAY.minusDays(1), TODAY.plusDays(5));
        return id;
    }

    private void interest(UUID user, UUID course) {
        jdbcTemplate.update(
            "INSERT INTO tb_course_interests (id, user_id, course_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
            UUID.randomUUID(), user, course);
    }

    private UUID enrollment(UUID user, UUID course, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, ?)",
            id, course, user, status);
        return id;
    }

    private void popularity(UUID course, String segment, String key, int windowDays, int score) {
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_popularity (
                    course_id, segment_type, segment_key, window_days, enrollment_count, view_count, score, computed_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?, CURRENT_TIMESTAMP)
                """,
            course, segment, key, windowDays, score, score);
    }
}
