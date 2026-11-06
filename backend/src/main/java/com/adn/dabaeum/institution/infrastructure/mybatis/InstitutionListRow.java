package com.adn.dabaeum.institution.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/**
 * 기관 목록(selectActivePage) 한 행. InstitutionRow 와 컬럼이 같고, 목록 전용 스칼라
 * 서브쿼리 다섯 개(instructor_count, course_count, learner_count, admin_count,
 * primary_admin_name)만 더 얹는다.
 * selectById 등 단건 조회는 이 서브쿼리를 계산하지 않는다.
 */
public record InstitutionListRow(
    UUID id,
    String institutionCode,
    String name,
    String businessNumber,
    String representativeName,
    String address,
    String contactPhone,
    String contactEmail,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    String homepageUrl,
    String region,
    UUID logoFileId,
    Long instructorCount,
    Long courseCount,
    Long learnerCount,
    Long adminCount,
    String primaryAdminName,
    String introduction
) {
}
