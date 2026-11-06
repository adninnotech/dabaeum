package com.adn.dabaeum.recommendation.infrastructure.mybatis;

import com.adn.dabaeum.recommendation.domain.CandidateFilter;
import com.adn.dabaeum.recommendation.domain.InterestCourse;
import com.adn.dabaeum.recommendation.domain.RecommendationImpression;
import com.adn.dabaeum.recommendation.domain.ReferenceCourse;
import com.adn.dabaeum.recommendation.domain.ScoredCourse;
import com.adn.dabaeum.recommendation.domain.SimilarCourse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecommendationMapper {

    List<ReferenceCourse> selectReferenceCourses(@Param("userId") UUID userId);

    List<SimilarCourse> selectSimilar(
        @Param("userId") UUID userId,
        @Param("filter") CandidateFilter filter,
        @Param("windowDays") int windowDays,
        @Param("freshSince") Instant freshSince,
        @Param("freshBonus") int freshBonus,
        @Param("limit") int limit
    );

    List<ScoredCourse> selectModelScored(
        @Param("userId") UUID userId,
        @Param("filter") CandidateFilter filter,
        @Param("limit") int limit
    );

    /** course_id 를 text 로 받는다(UUID 단일 컬럼 결과는 기본 핸들러가 없다). */
    List<InterestCourse> selectInterestCourses(
        @Param("codes") List<String> codes,
        @Param("filter") CandidateFilter filter,
        @Param("windowDays") int windowDays,
        @Param("limit") int limit
    );

    List<String> selectPopular(
        @Param("segmentType") String segmentType,
        @Param("segmentKey") String segmentKey,
        @Param("filter") CandidateFilter filter,
        @Param("windowDays") int windowDays,
        @Param("limit") int limit
    );

    int insertImpressions(@Param("rows") List<RecommendationImpression> rows);

    int markClicked(@Param("id") UUID id, @Param("userId") UUID userId, @Param("clickedAt") Instant clickedAt);

    int deleteImpressionsBefore(@Param("threshold") Instant threshold);
}
