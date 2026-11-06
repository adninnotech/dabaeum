package com.adn.dabaeum.enrollment.domain;

/** B-4 회원 상세 학습 통계. 요약 3값과 원장 집계 3값을 한 화면에 쓰는 형태로 합친 것이다. */
public record UserLearningStats(
    long totalCount,
    long inProgressCount,
    long completedCount,
    long totalStudyMinutes,
    int averageProgressPercent,
    long inquiryCount
) {

    /**
     * 요약의 세 값을 그대로 쓴다 — totalCount 는 신청 중까지 더한 전체 건수라
     * 요약 규칙(신청 중·수강 중·수강 종료)이 바뀌어도 통계가 따로 어긋나지 않는다.
     */
    public static UserLearningStats of(LearningSummary summary, LearningActivity activity) {
        return new UserLearningStats(
            summary.applying() + summary.inProgress() + summary.finished(),
            summary.inProgress(),
            summary.finished(),
            activity.totalStudyMinutes(),
            activity.averageProgressPercent(),
            activity.inquiryCount());
    }
}
