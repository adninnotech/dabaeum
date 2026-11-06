package com.adn.dabaeum.popularity.domain;

import java.time.Instant;
import java.util.List;

public interface PopularityRepository {

    /** 한 창(windowDays)의 집계를 통째로 다시 쓴다. 호출자가 트랜잭션·잠금을 잡는다. 쓴 행 수. */
    int recalculate(int windowDays, int enrollmentWeight, int viewWeight, Instant now);

    /** 점수 내림차순 상위 limit 개. 집계가 없으면 빈 목록. */
    List<PopularCourseScore> top(PopularitySegment segment, String key, int windowDays, int limit);
}
