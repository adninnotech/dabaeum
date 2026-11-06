package com.adn.dabaeum.user.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record UserAgreementRow(
    UUID id,
    UUID userId,
    String agreementType,
    Boolean agreed,
    Instant agreedAt
) {
}
