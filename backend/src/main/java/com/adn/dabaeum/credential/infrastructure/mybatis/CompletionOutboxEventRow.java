package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.UUID;

public record CompletionOutboxEventRow(
    UUID id,
    UUID aggregateId,
    String payloadJson,
    Integer retryCount
) {
}
