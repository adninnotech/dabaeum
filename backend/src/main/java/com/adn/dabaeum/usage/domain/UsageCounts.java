package com.adn.dabaeum.usage.domain;

/** 기간 합계 (docs/specs/usage-statistics.md §6.1). */
public record UsageCounts(long visitors, long searches, long courseViews, long enrollments, long bannerClicks) {

    public static final UsageCounts ZERO = new UsageCounts(0, 0, 0, 0, 0);
}
