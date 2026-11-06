package com.adn.dabaeum.popularity.infrastructure.mybatis;

import com.adn.dabaeum.popularity.domain.PopularCourseScore;
import com.adn.dabaeum.popularity.domain.PopularityRepository;
import com.adn.dabaeum.popularity.domain.PopularitySegment;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class PopularityMyBatisRepository implements PopularityRepository {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PopularityMapper mapper;

    public PopularityMyBatisRepository(PopularityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int recalculate(int windowDays, int enrollmentWeight, int viewWeight, Instant now) {
        Instant since = now.minus(Duration.ofDays(windowDays));
        mapper.deleteWindow(windowDays);
        return mapper.insertWindow(windowDays, enrollmentWeight, viewWeight, since,
            since.atZone(SEOUL).toLocalDate(), now);
    }

    @Override
    public List<PopularCourseScore> top(PopularitySegment segment, String key, int windowDays, int limit) {
        return mapper.selectTop(segment.name(), key, windowDays, limit);
    }
}
