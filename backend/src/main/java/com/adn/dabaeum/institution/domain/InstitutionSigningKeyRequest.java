package com.adn.dabaeum.institution.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 새 기관의 서명키 생성 요청. 기관을 만든 트랜잭션에서 outbox 에 쌓고, 커밋 뒤 키 생성 워커가 처리한다.
 * requestedBy 는 기관을 승인·생성한 관리자이며 KMS 작업 기록의 요청자가 된다.
 */
public record InstitutionSigningKeyRequest(
    UUID id,
    UUID institutionId,
    UUID requestedBy,
    Instant requestedAt
) {

    public InstitutionSigningKeyRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
