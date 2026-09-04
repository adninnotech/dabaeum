package com.adn.dabaeum.blockchain.api;

import java.time.Instant;
import java.util.UUID;

public record BlockchainAlertResponse(
    UUID id,
    String severity,
    String message,
    Instant occurredAt
) {
}
