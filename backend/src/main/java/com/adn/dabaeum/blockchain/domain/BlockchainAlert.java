package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.UUID;

public record BlockchainAlert(
    UUID id,
    String severity,
    String message,
    Instant occurredAt
) {
}
