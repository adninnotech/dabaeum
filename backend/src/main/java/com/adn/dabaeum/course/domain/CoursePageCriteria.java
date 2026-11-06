package com.adn.dabaeum.course.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CoursePageCriteria(
    int offset,
    int limit,
    CourseSort sort,
    CourseSortDirection direction,
    String keyword,
    CourseEducationType educationType,
    CourseSelectionMethod selectionMethod,
    CourseStatus status,
    /** PLATFORM_ADMIN 처럼 모든 기관의 DRAFT 를 볼 수 있으면 true — 그러면 아래 목록은 무시한다. */
    boolean includeAllDraft,
    /** 이 기관들의 DRAFT 는 볼 수 있다(그 기관 소속 INSTITUTION_ADMIN·INSTRUCTOR). */
    List<UUID> draftVisibleInstitutionIds,
    /** 분류 코드 접두사(TSIX_04 등). null 이면 분류 조건 없음. */
    String classificationPrefix
) {

    public CoursePageCriteria(
        int offset, int limit, CourseSort sort, CourseSortDirection direction, String keyword,
        CourseEducationType educationType, CourseSelectionMethod selectionMethod, CourseStatus status,
        boolean includeAllDraft, List<UUID> draftVisibleInstitutionIds
    ) {
        this(offset, limit, sort, direction, keyword, educationType, selectionMethod, status,
            includeAllDraft, draftVisibleInstitutionIds, null);
    }

    public CoursePageCriteria {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(direction, "direction");
        draftVisibleInstitutionIds = draftVisibleInstitutionIds == null
            ? List.of()
            : List.copyOf(draftVisibleInstitutionIds);
    }
}
