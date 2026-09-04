package com.adn.dabaeum.authentication.infrastructure.mybatis;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class LocalAccountMyBatisRepository implements LocalAccountRepository {

    private final LocalAccountMapper mapper;

    public LocalAccountMyBatisRepository(LocalAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(LocalAccountCredential credential) {
        mapper.insert(toRow(credential));
    }

    @Override
    public Optional<LocalAccountCredential> findByUserId(java.util.UUID userId) {
        return Optional.ofNullable(mapper.selectByUserId(userId)).map(this::toDomain);
    }

    @Override
    public boolean updatePasswordHash(
        java.util.UUID userId, String passwordHash, java.time.Instant updatedAt
    ) {
        return mapper.updatePasswordHash(userId, passwordHash, updatedAt) == 1;
    }

    @Override
    public Optional<LocalAccountCredential> findByNormalizedEmail(
        String normalizedEmail
    ) {
        return Optional.ofNullable(mapper.selectByNormalizedEmail(normalizedEmail))
            .map(this::toDomain);
    }

    private LocalAccountRow toRow(LocalAccountCredential credential) {
        return new LocalAccountRow(
            credential.identityId(),
            credential.userId(),
            credential.normalizedEmail(),
            credential.passwordHash(),
            credential.createdAt(),
            credential.updatedAt()
        );
    }

    private LocalAccountCredential toDomain(LocalAccountRow row) {
        return new LocalAccountCredential(
            row.identityId(),
            row.userId(),
            row.normalizedEmail(),
            row.passwordHash(),
            row.createdAt(),
            row.updatedAt()
        );
    }
}
