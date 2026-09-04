package com.adn.dabaeum.file.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.config.FileStorageProperties;
import com.adn.dabaeum.file.domain.FileStoragePort;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultFileApplicationService implements FileApplicationService {

    /** content-type → 저장 확장자. 확장자는 사용자 파일명이 아니라 여기서 결정한다. */
    private static final Map<String, String> IMAGE_TYPES = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp");
    private static final Map<String, String> DOCUMENT_TYPES = Map.of(
        "application/pdf", "pdf");
    private static final Set<StoredFilePurpose> DOCUMENT_ALLOWED =
        Set.of(StoredFilePurpose.INQUIRY);

    private final StoredFileRepository fileRepository;
    private final FileStoragePort storage;
    private final FileStorageProperties properties;

    public DefaultFileApplicationService(
        StoredFileRepository fileRepository,
        FileStoragePort storage,
        FileStorageProperties properties
    ) {
        this.fileRepository = fileRepository;
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    @Transactional
    public StoredFile upload(UploadFileCommand command) {
        Objects.requireNonNull(command, "command");
        StoredFilePurpose purpose = parsePurpose(command.purpose());
        if (command.content() == null || command.size() < 1) {
            throw validation("file");
        }
        if (command.size() > properties.maxSize().toBytes()) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.FILE_SIZE_EXCEEDED,
                "File exceeds the maximum size of " + properties.maxSize(),
                List.of("file"));
        }
        String extension = extensionFor(purpose, command.contentType());
        String originalName = normalizeName(command.originalName(), extension);

        String storagePath = storage.store(command.actor().userId(), extension, command.content());
        StoredFile file = new StoredFile(
            UUID.randomUUID(), purpose, originalName, command.contentType().trim(),
            command.size(), storagePath, command.actor().userId(), command.requestedAt());
        try {
            fileRepository.save(file);
        } catch (RuntimeException exception) {
            // DB 실패 시 디스크에 남은 바이트를 정리한다.
            storage.delete(storagePath);
            throw exception;
        }
        return file;
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownload download(UUID fileId) {
        StoredFile file = fileRepository.findById(fileId)
            .orElseThrow(this::notFound);
        try {
            return new FileDownload(file, storage.open(file.storagePath()));
        } catch (RuntimeException exception) {
            // 메타는 있으나 디스크에 파일이 없는 경우도 404로 처리한다.
            throw notFound();
        }
    }

    private StoredFilePurpose parsePurpose(String value) {
        if (value == null || value.isBlank()) {
            throw validation("purpose");
        }
        try {
            return StoredFilePurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validation("purpose");
        }
    }

    private String extensionFor(StoredFilePurpose purpose, String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        String extension = IMAGE_TYPES.get(normalized);
        if (extension == null && DOCUMENT_ALLOWED.contains(purpose)) {
            extension = DOCUMENT_TYPES.get(normalized);
        }
        if (extension == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.FILE_TYPE_NOT_SUPPORTED,
                "File type is not supported for " + purpose,
                List.of("file"));
        }
        return extension;
    }

    private String normalizeName(String originalName, String extension) {
        String name = originalName == null ? "" : originalName.trim();
        // 경로 구분자를 제거해 이름만 남긴다.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty()) {
            name = "upload." + extension;
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.FILE_NOT_FOUND, "File not found");
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
