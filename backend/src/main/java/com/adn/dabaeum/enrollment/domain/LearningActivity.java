package com.adn.dabaeum.enrollment.domain;

/**
 * 학습 통계 중 원장 집계가 필요한 세 값. 신청·수강·종료 건수는 {@link LearningSummary} 가
 * 이미 세므로 여기서 다시 세지 않는다. 둘을 합쳐 {@link UserLearningStats} 가 된다.
 */
public record LearningActivity(
    long totalStudyMinutes,
    int averageProgressPercent,
    long inquiryCount
) {
}
