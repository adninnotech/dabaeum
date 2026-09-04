package com.adn.dabaeum.file.api;

import com.adn.dabaeum.file.domain.StoredFilePurpose;
import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(
    UUID id,
    String url,
    StoredFilePurpose purpose,
    String originalName,
    String contentType,
    long size,
    Instant createdAt
) {
}
