package com.adn.dabaeum.file.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.file.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class LocalDiskFileStorageTest {

    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");

    @TempDir
    Path root;

    private LocalDiskFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskFileStorage(
            new FileStorageProperties(root.toString(), DataSize.ofMegabytes(10)));
    }

    @Test
    void storesUnderUserImageDirectoryWithUuidNameAndReadsBack() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        String relative = storage.store(USER_ID, "png", new ByteArrayInputStream(bytes));

        assertThat(relative).matches(
            USER_ID + "/image/[0-9a-f-]{36}\\.png");
        assertThat(Files.readAllBytes(root.resolve(relative))).isEqualTo(bytes);
        try (InputStream opened = storage.open(relative)) {
            assertThat(opened.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void deleteRemovesFileAndIgnoresMissing() {
        String relative = storage.store(
            USER_ID, "jpg", new ByteArrayInputStream(new byte[] {1, 2, 3}));
        assertThat(root.resolve(relative)).exists();

        storage.delete(relative);
        assertThat(root.resolve(relative)).doesNotExist();
        storage.delete(relative);
    }

    @Test
    void rejectsPathsThatEscapeTheRoot() {
        assertThatThrownBy(() -> storage.open("../outside.txt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes");
        assertThatThrownBy(() -> storage.open(USER_ID + "/image/../../../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.open(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidExtensions() {
        assertThatThrownBy(() -> storage.store(
            USER_ID, "../x", new ByteArrayInputStream(new byte[] {1})))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(
            USER_ID, "PNG", new ByteArrayInputStream(new byte[] {1})))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
