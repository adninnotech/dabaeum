package com.adn.dabaeum.view.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface CourseViewRepository {

    /** 삭제되지 않았고 DRAFT·CANCELLED 가 아닌 강좌인지(인기 대상과 같은 기준). */
    boolean isPublicCourse(UUID courseId);

    /** 하루 1건. 이미 있으면 아무것도 하지 않는다. */
    void record(UUID courseId, String viewerKey, LocalDate viewDate, UUID userId, Instant at);

    /** 보존 기간(§14, 1년) 지난 조회 로그 삭제. */
    int deleteViewsBefore(LocalDate date);
}
