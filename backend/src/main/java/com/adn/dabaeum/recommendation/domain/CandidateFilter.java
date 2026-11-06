package com.adn.dabaeum.recommendation.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 후보 강좌 제외 조건 (설계 §3.4). 어느 순위(§3, §4)에서 뽑든 같이 적용한다.
 *
 * @param userId 로그인 사용자. null 이면 비로그인(찜·신청 제외를 건너뛴다)
 * @param excludeCourseIds 홈 인기 섹션에 이미 그린 강좌
 * @param today 모집 기간 판정 기준일(서울)
 * @param adultViewer 성인 사용자면 어린이·아동청소년 전용 강좌를 뺀다
 */
public record CandidateFilter(
    UUID userId,
    List<UUID> excludeCourseIds,
    LocalDate today,
    boolean adultViewer
) {

    public CandidateFilter {
        excludeCourseIds = List.copyOf(Objects.requireNonNullElse(excludeCourseIds, List.of()));
        Objects.requireNonNull(today, "today");
    }

    /** 이미 고른 강좌를 더 빼고 다음 순위를 뽑을 때. */
    public CandidateFilter excludingAlso(List<UUID> moreCourseIds) {
        if (moreCourseIds == null || moreCourseIds.isEmpty()) {
            return this;
        }
        List<UUID> merged = new ArrayList<>(excludeCourseIds);
        merged.addAll(moreCourseIds);
        return new CandidateFilter(userId, merged, today, adultViewer);
    }
}
