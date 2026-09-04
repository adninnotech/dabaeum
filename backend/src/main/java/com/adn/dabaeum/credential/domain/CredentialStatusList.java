package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 발급기관 하나가 소유하는 Bitstring Status List. 한 칸이 VC 한 장의 폐기 여부를 뜻한다. */
public record CredentialStatusList(
    UUID id,
    UUID institutionId,
    int listNo,
    String statusPurpose,
    int capacity,
    Instant createdAt
) {

    public static final String REVOCATION = "revocation";
    /** W3C Bitstring Status List v1.0 이 요구하는 최소 비트 수. */
    public static final int MINIMUM_CAPACITY = 131_072;

    public CredentialStatusList {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (listNo < 1) {
            throw new IllegalArgumentException("listNo must be positive");
        }
        if (!REVOCATION.equals(statusPurpose)) {
            throw new IllegalArgumentException("statusPurpose is unsupported");
        }
        if (capacity < MINIMUM_CAPACITY) {
            throw new IllegalArgumentException("capacity must be at least " + MINIMUM_CAPACITY);
        }
    }
}
