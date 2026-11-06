package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** §12.2 원자 소비 UPDATE 의 WHERE 조건 전부. 하나라도 다르면 0행이다. */
public record KmsApprovalConsumption(
    UUID approvalId,
    UUID institutionId,
    SignKeyPurpose purpose,
    KmsAction action,
    UUID requester,
    UUID signKeyId,
    int currentVersion,
    Integer targetKeyVersion,
    Instant targetCompromisedAt,
    String paramsHash,
    UUID operationId,
    Instant now
) {

    public KmsApprovalConsumption {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(signKeyId, "signKeyId");
        Objects.requireNonNull(paramsHash, "paramsHash");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(now, "now");
    }
}
