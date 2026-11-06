package com.adn.dabaeum.enrollment.domain;

import java.time.Instant;
import java.util.Objects;

/** 강좌별 수강 신청 목록의 한 행. 신청 정보와 신청자 정보, 대기 순번을 한 번의 조회로 묶는다. */
public record EnrollmentListItem(
    Enrollment enrollment,
    EnrolleeProfile profile,
    Integer waitlistPosition,
    int progressPercent,
    /** 마지막 출석(PRESENT·LATE) 시각. 출석 기록이 없으면 null. */
    Instant lastStudiedAt
) {

    public EnrollmentListItem {
        Objects.requireNonNull(enrollment, "enrollment");
        Objects.requireNonNull(profile, "profile");
    }
}
