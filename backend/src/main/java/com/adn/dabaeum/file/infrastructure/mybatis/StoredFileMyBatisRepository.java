package com.adn.dabaeum.file.infrastructure.mybatis;

import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class StoredFileMyBatisRepository implements StoredFileRepository {

    private final StoredFileMapper mapper;

    public StoredFileMyBatisRepository(StoredFileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(StoredFile file) {
        mapper.insert(new StoredFileRow(
            file.id(), file.purpose().name(), file.originalName(), file.contentType(),
            file.size(), file.storagePath(), file.uploadedBy(), file.createdAt()));
    }

    @Override
    public Optional<StoredFile> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(row -> new StoredFile(
                row.id(), StoredFilePurpose.valueOf(row.purpose()), row.originalName(),
                row.contentType(), row.size(), row.storagePath(), row.uploadedBy(),
                row.createdAt()));
    }
}
