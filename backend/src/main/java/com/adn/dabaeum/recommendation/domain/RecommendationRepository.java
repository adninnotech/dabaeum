package com.adn.dabaeum.recommendation.domain;

import com.adn.dabaeum.popularity.domain.PopularitySegment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RecommendationRepository {

    /** 사용자의 찜·신청(대기·승인·수료 포함) 강좌. 분류 코드가 없는 강좌는 뺀다. 최근 신호 순. */
    List<ReferenceCourse> findReferenceCourses(UUID userId);

    /**
     * 기준 강좌와 분류가 비슷한 모집 중 강좌 (설계 §3.2~3.4). 정렬: 일치 수준 → 인기 점수(신규 보정) → 모집 마감 → 최신.
     *
     * @param windowDays 인기 집계 창 (tb_course_popularity.window_days)
     * @param freshSince 이 시각 이후 등록된 강좌는 freshBonus 를 더한다
     */
    List<SimilarCourse> findSimilar(
        UUID userId, CandidateFilter filter, int windowDays, Instant freshSince, int freshBonus, int limit
    );

    /**
     * 야간 모델 배치가 매긴 점수 행 (설계 §12.5). §3.4 제외 조건과 같은 기관·같은 제목 반복 제거를 적용한 뒤
     * 점수 → 모집 마감 순으로 limit 개. 점수 행이 없으면 빈 목록.
     */
    List<ScoredCourse> findModelScored(UUID userId, CandidateFilter filter, int limit);

    /** 세그먼트 인기 순으로 모집 중 강좌 id (설계 §4). 제외 조건을 적용한 뒤 limit 개. 집계가 없으면 빈 목록. */
    List<UUID> findPopular(PopularitySegment segment, String segmentKey, CandidateFilter filter, int windowDays, int limit);

    /**
     * 관심 분야(1단계 분류)에 드는 모집 중 강좌. 전체 인기 → 모집 마감 순. 인기 집계가 없는 강좌도 뒤에 포함한다.
     * 제외 조건과 같은 기관·같은 제목 1건 규칙을 적용한다.
     */
    List<InterestCourse> findInterestCourses(List<String> codes, CandidateFilter filter, int windowDays, int limit);

    void saveImpressions(List<RecommendationImpression> impressions);

    /** 본인의 노출 행에 첫 클릭 시각을 적는다. 행이 없거나 남의 것이거나 이미 클릭했으면 false. */
    boolean markClicked(UUID impressionId, UUID userId, Instant clickedAt);

    int deleteImpressionsBefore(Instant threshold);
}
