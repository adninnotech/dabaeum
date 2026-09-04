package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CredentialStatusListRow(
    UUID id,
    UUID institutionId,
    Integer listNo,
    String statusPurpose,
    Integer capacity,
    Instant createdAt
) {
}
