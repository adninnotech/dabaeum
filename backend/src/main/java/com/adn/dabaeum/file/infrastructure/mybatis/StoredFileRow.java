package com.adn.dabaeum.file.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record StoredFileRow(
    UUID id,
    String purpose,
    String originalName,
    String contentType,
    Long size,
    String storagePath,
    UUID uploadedBy,
    Instant createdAt
) {
}
