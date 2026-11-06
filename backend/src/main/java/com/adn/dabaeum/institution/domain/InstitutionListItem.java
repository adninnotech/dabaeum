package com.adn.dabaeum.institution.domain;

import java.util.Objects;

/**
 * 관리자 기관 목록의 한 행. 소속 강사 수·개설 과정 수·승인 학습자 수·기관 관리자 수는 집계라
 * Institution 애그리거트에 넣지 않는다(CourseView 의 enrolledCount 와 같은 이유) —
 * 목록 조회에서만 채우고 단건 조회(findActiveById 등)에는 없다.
 * primaryAdminName 은 가장 먼저 배정된 기관 관리자 이름이다. 관리자가 없으면 null.
 */
public record InstitutionListItem(
    Institution institution,
    long instructorCount,
    long courseCount,
    long learnerCount,
    long adminCount,
    String primaryAdminName
) {

    public InstitutionListItem {
        Objects.requireNonNull(institution, "institution");
    }
}
