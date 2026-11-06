package com.adn.dabaeum.usage.domain;

/** 추이 한 칸. period 는 DAY 면 YYYY-MM-DD, MONTH 면 YYYY-MM. 빈 칸도 0 으로 온다. */
public record UsageTrendRow(
    String period, long visitors, long searches, long courseViews, long enrollments, long bannerClicks
) {
}
