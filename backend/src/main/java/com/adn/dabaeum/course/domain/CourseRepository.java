package com.adn.dabaeum.course.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    void save(Course course);

    Optional<Course> findById(UUID id);

    Optional<Course> findActiveById(UUID id);

    Optional<Course> findActiveByIdForUpdate(UUID id);

    Optional<Course> findActiveByInstitutionAndCode(
        UUID institutionId,
        String courseCode
    );

    List<Course> findActivePage(CoursePageCriteria criteria);

    /**
     * q·educationType·selectionMethod·status 필터가 있는 목록용. findActiveViewPage 의 조건과
     * 반드시 같아야 한다. includeAllDraft·draftVisibleInstitutionIds 는 DRAFT 가시성 제한 —
     * true 면 제한 없음(PLATFORM_ADMIN), 아니면 draftVisibleInstitutionIds 에 속한 기관의
     * DRAFT 만 status<>DRAFT 인 행과 함께 보인다.
     */
    long countActive(
        String keyword,
        CourseEducationType educationType,
        CourseSelectionMethod selectionMethod,
        CourseStatus status,
        boolean includeAllDraft,
        List<UUID> draftVisibleInstitutionIds,
        String classificationPrefix
    );

    boolean updateActive(Course course);

    /** 삭제되지 않은 DRAFT 강좌만 지운 것으로 표시한다(soft delete). 표시했으면 true. */
    boolean softDeleteDraft(UUID id, Instant deletedAt);

    /** 목록·상세 조회용. 승인된 신청자 수를 함께 가져온다. */
    Optional<CourseView> findActiveViewById(UUID id);

    /** 삭제되지 않은 강좌만, 순서는 보장하지 않는다. */
    List<CourseView> findActiveViewsByIds(List<UUID> ids);

    List<CourseView> findActiveViewPage(CoursePageCriteria criteria);
}
