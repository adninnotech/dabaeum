package com.adn.dabaeum.enrollment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentProgressView(
    UUID enrollmentId,
    UUID userId,
    EnrolleeProfile profile,
    UUID courseId,
    String courseTitle,
    long attendedCount,
    long sessionCount,
    /** 마지막 출석(PRESENT·LATE) 시각. 출석 기록이 없으면 null. */
    Instant lastStudiedAt,
    String status,
    /**
     * 출석률(0~100, 소수 둘째 자리). 학습자 출결 요약과 같은 규칙으로 서비스가 채운다. 저장소에서 막 읽은 값은 null.
     * progressPercent 는 강좌 전체 회차 대비 학습 진도라 다른 숫자다.
     */
    BigDecimal attendanceRate
) {

    public EnrollmentProgressView withAttendanceRate(BigDecimal rate) {
        return new EnrollmentProgressView(enrollmentId, userId, profile, courseId, courseTitle,
            attendedCount, sessionCount, lastStudiedAt, status, rate);
    }

    public int progressPercent() {
        return progressPercent(attendedCount, sessionCount);
    }

    /** 강좌별 수강현황 목록에서도 같은 식을 쓴다 — 두 화면이 다른 숫자를 보이면 안 된다. */
    public static int progressPercent(long attendedCount, long sessionCount) {
        if (sessionCount <= 0) {
            return 0;
        }
        long percent = Math.round(attendedCount * 100.0 / sessionCount);
        return (int) Math.min(100, Math.max(0, percent));
    }
}
