package com.adn.dabaeum.enrollment.api;

/** B-4 회원 상세 학습 통계 카드. */
public record UserLearningStatsResponse(
    long totalCount,
    long inProgressCount,
    long completedCount,
    long totalStudyMinutes,
    int averageProgressPercent,
    long inquiryCount
) {
}
