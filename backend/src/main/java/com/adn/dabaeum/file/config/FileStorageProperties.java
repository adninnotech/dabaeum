package com.adn.dabaeum.file.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "dabaeum.files")
public record FileStorageProperties(
    String root,
    DataSize maxSize
) {

    public FileStorageProperties {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("dabaeum.files.root is required");
        }
        root = root.trim();
        if (maxSize == null || maxSize.toBytes() < 1) {
            throw new IllegalArgumentException("dabaeum.files.max-size must be positive");
        }
    }

    public Path rootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }
}
