package com.adn.dabaeum.file.infrastructure.mybatis;

import com.adn.dabaeum.file.domain.FileAccessRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class FileAccessMyBatisRepository implements FileAccessRepository {

    private final FileAccessMapper mapper;

    public FileAccessMyBatisRepository(FileAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean publiclyLinked(UUID fileId, String purpose, Instant now) {
        return mapper.publiclyLinked(fileId, purpose, now);
    }

    @Override
    public boolean linked(UUID fileId, String purpose) {
        return mapper.linked(fileId, purpose);
    }

    @Override
    public boolean managedByInstitution(UUID fileId, String purpose, UUID institutionId) {
        return mapper.managedByInstitution(fileId, purpose, institutionId);
    }
}
