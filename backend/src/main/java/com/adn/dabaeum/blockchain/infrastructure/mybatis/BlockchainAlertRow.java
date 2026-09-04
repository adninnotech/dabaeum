package com.adn.dabaeum.blockchain.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record BlockchainAlertRow(
    UUID id,
    String severity,
    String message,
    Instant occurredAt
) {
}
