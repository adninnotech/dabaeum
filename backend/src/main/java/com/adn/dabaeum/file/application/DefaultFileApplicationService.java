package com.adn.dabaeum.file.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.config.FileStorageProperties;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.FileStoragePort;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    /** 첨부파일은 문서를 받는 것이 목적이다. 나머지(프로필·썸네일·로고·배너)는 이미지만 받는다. */
    private static final Set<StoredFilePurpose> DOCUMENT_ALLOWED = Set.of(
        StoredFilePurpose.COURSE_ATTACHMENT,
        StoredFilePurpose.NOTICE_ATTACHMENT);
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10
    };
    private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_SIGNATURE = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final StoredFileRepository fileRepository;
    private final FileStoragePort storage;
    private final FileStorageProperties properties;
    private final FileAccessPolicy accessPolicy;

    public DefaultFileApplicationService(
        StoredFileRepository fileRepository,
        FileStoragePort storage,
        FileStorageProperties properties,
        FileAccessPolicy accessPolicy
    ) {
        this.fileRepository = fileRepository;
        this.storage = storage;
        this.properties = properties;
        this.accessPolicy = accessPolicy;
    }

    @Override
    @Transactional
    public StoredFile upload(UploadFileCommand command) {
        Objects.requireNonNull(command, "command");
        StoredFilePurpose purpose = parsePurpose(command.purpose());
        if (purpose == StoredFilePurpose.INQUIRY) {
            throw validation("purpose");
        }
        if (command.content() == null || command.size() < 1) {
            throw validation("file");
        }
        if (command.size() > properties.maxSize().toBytes()) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.FILE_SIZE_EXCEEDED,
                "File exceeds the maximum size of " + properties.maxSize(),
                List.of("file"));
        }
        String extension = extensionFor(purpose, command.contentType());
        InputStream content = verifiedContent(command.content(), extension, purpose);
        String originalName = normalizeName(command.originalName(), extension);

        String storagePath = storage.store(command.actor().userId(), extension, content);
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
    public FileDownload download(UUID fileId, AuthenticatedUserContext actor) {
        StoredFile file = fileRepository.findById(fileId)
            .orElseThrow(this::notFound);
        if (!accessPolicy.canRead(file, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN,
                "File access is forbidden");
        }
        try {
            return new FileDownload(file, storage.open(file.storagePath()),
                accessPolicy.publiclyReadable(file));
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
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.FILE_TYPE_NOT_SUPPORTED,
                "File type is not supported for " + purpose,
                List.of("file"));
        }
        return extension;
    }

    private InputStream verifiedContent(InputStream content, String extension,
        StoredFilePurpose purpose) {
        BufferedInputStream buffered = new BufferedInputStream(content);
        try {
            buffered.mark(12);
            byte[] header = buffered.readNBytes(12);
            buffered.reset();
            boolean matches = switch (extension) {
                case "jpg" -> header.length >= 3
                    && header[0] == (byte) 0xff && header[1] == (byte) 0xd8
                    && header[2] == (byte) 0xff;
                case "png" -> startsWith(header, PNG_SIGNATURE);
                case "webp" -> header.length >= 12
                    && startsWith(header, RIFF_SIGNATURE)
                    && Arrays.equals(header, 8, 12, WEBP_SIGNATURE, 0, 4);
                case "pdf" -> startsWith(header, PDF_SIGNATURE);
                default -> false;
            };
            if (!matches) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    ApiErrorCode.FILE_TYPE_NOT_SUPPORTED,
                    "File type is not supported for " + purpose, List.of("file"));
            }
            return buffered;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to inspect uploaded file", exception);
        }
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
            && Arrays.equals(content, 0, signature.length,
                signature, 0, signature.length);
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
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
