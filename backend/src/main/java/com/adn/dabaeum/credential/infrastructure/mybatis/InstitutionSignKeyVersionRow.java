package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InstitutionSignKeyVersionRow(
    UUID id,
    UUID signKeyId,
    UUID institutionId,
    Integer keyVersion,
    String kidFragment,
    String publicKeyJwk,
    String source,
    String status,
    Instant openbaoCreatedAt,
    Instant activatedAt,
    Instant retiredAt,
    Instant compromisedAt,
    UUID createdBy,
    Instant createdAt
) {
}
