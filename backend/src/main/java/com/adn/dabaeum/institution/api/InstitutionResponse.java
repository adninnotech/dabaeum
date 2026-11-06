package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.institution.domain.InstitutionAdministrator;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstitutionResponse(
    UUID id,
    String institutionCode,
    String name,
    String businessNumber,
    String representativeName,
    String address,
    String contactPhone,
    String contactEmail,
    InstitutionStatus status,
    Instant createdAt,
    Instant updatedAt,
    String homepageUrl,
    String region,
    UUID logoFileId,
    String introduction,

    // 상세 조회에서만 채운다. 목록은 행마다 조회하면 N+1 이 되므로 항상 null(응답에서 생략)이다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<InstitutionAdministrator> administrators,

    // administrators 와 반대 방향 — 목록 조회에서만 채운다. 상세 조회는 항상
    // null(응답에서 생략)이다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long instructorCount,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long courseCount,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long learnerCount,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long adminCount,
    // 목록에서도 관리자가 없으면 null 이라 생략된다. adminCount 가 0 이면 없는 것이다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String primaryAdminName
) {
}
