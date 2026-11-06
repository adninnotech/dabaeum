package com.adn.dabaeum.institution.domain;

import java.time.Instant;
import java.util.UUID;

public record Institution(
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
    Instant deletedAt,
    String homepageUrl,
    String region,
    UUID logoFileId,
    String introduction
) {

    // 읽기 경로(조회)도 이 컴팩트 생성자를 통과한다. 기존 행에 이미 들어있는 값이
    // 이 검증을 못 넘기면 조회만 했는데 500이 나므로, 형식이 아니라 길이만 본다.
    public Institution {
        if (homepageUrl != null && homepageUrl.length() > 500) {
            throw new IllegalArgumentException("homepageUrl must not exceed 500 characters");
        }
        if (region != null && region.length() > 50) {
            throw new IllegalArgumentException("region must not exceed 50 characters");
        }
    }

    // 새 필드 이전 호출부(12개 인자)가 전부 다시 조립하지 않도록 남긴 호환 생성자.
    public Institution(
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
        Instant deletedAt
    ) {
        this(id, institutionCode, name, businessNumber, representativeName, address,
            contactPhone, contactEmail, status, createdAt, updatedAt, deletedAt,
            null, null, null, null);
    }

    public Institution(
        UUID id, String institutionCode, String name, String businessNumber,
        String representativeName, String address, String contactPhone,
        String contactEmail, InstitutionStatus status, Instant createdAt,
        Instant updatedAt, Instant deletedAt, String homepageUrl, String region,
        UUID logoFileId
    ) {
        this(id, institutionCode, name, businessNumber, representativeName, address,
            contactPhone, contactEmail, status, createdAt, updatedAt, deletedAt,
            homepageUrl, region, logoFileId, null);
    }
}
