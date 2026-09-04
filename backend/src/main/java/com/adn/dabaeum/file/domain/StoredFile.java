package com.adn.dabaeum.file.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 업로드된 파일의 메타데이터. 바이트는 파일 저장소에 있고 DB에는 루트 기준 상대경로만 둔다.
 */
public record StoredFile(
    UUID id,
    StoredFilePurpose purpose,
    String originalName,
    String contentType,
    long size,
    String storagePath,
    UUID uploadedBy,
    Instant createdAt
) {

    public StoredFile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purpose, "purpose");
        if (originalName == null || originalName.isBlank() || originalName.length() > 255) {
            throw new IllegalArgumentException("originalName must be 1..255 characters");
        }
        if (contentType == null || contentType.isBlank() || contentType.length() > 100) {
            throw new IllegalArgumentException("contentType must be 1..100 characters");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (storagePath == null || storagePath.isBlank() || storagePath.length() > 500) {
            throw new IllegalArgumentException("storagePath must be 1..500 characters");
        }
        Objects.requireNonNull(uploadedBy, "uploadedBy");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
