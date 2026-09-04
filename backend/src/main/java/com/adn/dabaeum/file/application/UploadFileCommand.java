package com.adn.dabaeum.file.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.io.InputStream;
import java.time.Instant;

public record UploadFileCommand(
    String purpose,
    String originalName,
    String contentType,
    long size,
    InputStream content,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
