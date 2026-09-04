package com.adn.dabaeum.file.domain;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository {

    void save(StoredFile file);

    Optional<StoredFile> findById(UUID id);
}
