package com.adn.dabaeum.file.infrastructure.storage;

import com.adn.dabaeum.file.config.FileStorageProperties;
import com.adn.dabaeum.file.domain.FileStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크 저장소. 루트 아래 {@code <userId>/image/<uuid>.<ext>}로 저장하며
 * 정규화 후 루트를 벗어나는 경로는 거부한다.
 */
@Component
public class LocalDiskFileStorage implements FileStoragePort {

    private static final String IMAGE_DIRECTORY = "image";
    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,8}$");

    private final Path root;

    public LocalDiskFileStorage(FileStorageProperties properties) {
        this.root = properties.rootPath();
    }

    @Override
    public String store(UUID userId, String extension, InputStream content) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(content, "content");
        if (extension == null || !EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("extension is invalid");
        }
        String relativePath = userId + "/" + IMAGE_DIRECTORY + "/"
            + UUID.randomUUID() + "." + extension;
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store file", exception);
        }
        return relativePath;
    }

    @Override
    public InputStream open(String relativePath) {
        Path target = resolve(relativePath);
        try {
            return Files.newInputStream(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open file", exception);
        }
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete file", exception);
        }
    }

    Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("relativePath escapes storage root");
        }
        return resolved;
    }
}
