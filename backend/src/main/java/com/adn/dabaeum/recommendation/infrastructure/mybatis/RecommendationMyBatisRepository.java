package com.adn.dabaeum.recommendation.infrastructure.mybatis;

import com.adn.dabaeum.popularity.domain.PopularitySegment;
import com.adn.dabaeum.recommendation.domain.CandidateFilter;
import com.adn.dabaeum.recommendation.domain.InterestCourse;
import com.adn.dabaeum.recommendation.domain.RecommendationImpression;
import com.adn.dabaeum.recommendation.domain.RecommendationRepository;
import com.adn.dabaeum.recommendation.domain.ReferenceCourse;
import com.adn.dabaeum.recommendation.domain.ScoredCourse;
import com.adn.dabaeum.recommendation.domain.SimilarCourse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class RecommendationMyBatisRepository implements RecommendationRepository {

    private final RecommendationMapper mapper;

    public RecommendationMyBatisRepository(RecommendationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ReferenceCourse> findReferenceCourses(UUID userId) {
        return mapper.selectReferenceCourses(userId);
    }

    @Override
    public List<SimilarCourse> findSimilar(
        UUID userId, CandidateFilter filter, int windowDays, Instant freshSince, int freshBonus, int limit
    ) {
        return mapper.selectSimilar(userId, filter, windowDays, freshSince, freshBonus, limit);
    }

    @Override
    public List<ScoredCourse> findModelScored(UUID userId, CandidateFilter filter, int limit) {
        return mapper.selectModelScored(userId, filter, limit);
    }

    @Override
    public List<UUID> findPopular(
        PopularitySegment segment, String segmentKey, CandidateFilter filter, int windowDays, int limit
    ) {
        return mapper.selectPopular(segment.name(), segmentKey, filter, windowDays, limit).stream()
            .map(UUID::fromString)
            .toList();
    }

    @Override
    public List<InterestCourse> findInterestCourses(
        List<String> codes, CandidateFilter filter, int windowDays, int limit
    ) {
        return mapper.selectInterestCourses(codes, filter, windowDays, limit);
    }

    @Override
    public void saveImpressions(List<RecommendationImpression> impressions) {
        if (impressions.isEmpty()) {
            return;
        }
        mapper.insertImpressions(impressions);
    }

    @Override
    public boolean markClicked(UUID impressionId, UUID userId, Instant clickedAt) {
        return mapper.markClicked(impressionId, userId, clickedAt) == 1;
    }

    @Override
    public int deleteImpressionsBefore(Instant threshold) {
        return mapper.deleteImpressionsBefore(threshold);
    }
}
