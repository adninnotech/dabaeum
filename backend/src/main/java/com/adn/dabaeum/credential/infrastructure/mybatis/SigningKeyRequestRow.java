package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.UUID;

public record SigningKeyRequestRow(
    UUID id,
    String institutionId,
    String requestedBy,
    Integer retryCount
) {
}
