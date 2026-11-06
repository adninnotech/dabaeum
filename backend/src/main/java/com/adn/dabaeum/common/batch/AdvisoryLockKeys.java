package com.adn.dabaeum.common.batch;

/**
 * 서버가 여러 대일 때 주기 작업을 한 대만 돌리기 위한 advisory lock 키 (설계서 §2.1).
 * 키는 여기에만 둔다. 한 번 배포한 값은 바꾸지 않는다 — 바꾸면 구버전 서버와 새 서버가 동시에 돈다.
 */
public final class AdvisoryLockKeys {

    /** 만료 후 1일 지난 다대구 가입 토큰 삭제 (설계서 §4.2, §14). */
    public static final long REGISTRATION_TOKEN_CLEANUP = 7_340_001L;

    /** 2년 지난 관리자 접속기록 삭제 (설계서 §10.2, §14). */
    public static final long ADMIN_ACCESS_LOG_CLEANUP = 7_340_002L;

    /** 1년 지난 강좌 조회 로그 삭제 (설계서 §8.1, §14). */
    public static final long COURSE_VIEW_CLEANUP = 7_340_003L;

    /** 1년 지난 조회 salt 삭제 (설계서 §8.1, §14). */
    public static final long VIEW_SALT_CLEANUP = 7_340_004L;

    /** 인기 점수 재계산 (설계서 §8.2). */
    public static final long POPULARITY_RECALCULATION = 7_340_005L;

    /** 만료·폐기 후 90일 지난 포트폴리오 공유 링크 삭제 (설계서 §6.2, §14). */
    public static final long PORTFOLIO_SHARE_CLEANUP = 7_340_006L;

    /** 1년 지난 추천 노출 로그 삭제 (docs/specs/course-recommendation.md §7). */
    public static final long RECOMMENDATION_IMPRESSION_CLEANUP = 7_340_007L;

    /** 추천 모델 학습·점수 기록 (docs/specs/course-recommendation.md §12.2). */
    public static final long RECOMMENDATION_MODEL_TRAINING = 7_340_008L;

    /** 1년 지난 이용 통계 원문 기록 삭제 (docs/specs/usage-statistics.md §9). */
    public static final long USAGE_EVENT_CLEANUP = 7_340_009L;

    /** 이용 통계 일별 집계 (docs/specs/usage-statistics.md §5). */
    public static final long USAGE_DAILY_AGGREGATION = 7_340_010L;

    /** 그날이 지난 이용 통계 salt 삭제 (docs/specs/usage-statistics.md §3.1, §9). */
    public static final long USAGE_SALT_CLEANUP = 7_340_011L;

    private AdvisoryLockKeys() {
    }
}
