package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CredentialMyBatisRepository implements CredentialRepository {

    private final CredentialMapper mapper;

    public CredentialMyBatisRepository(CredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Credential> findById(UUID credentialId) {
        return Optional.ofNullable(mapper.selectById(credentialId)).map(CredentialMyBatisRepository::toDomain);
    }

    @Override
    public Optional<Credential> findByCredentialNo(String credentialNo) {
        return Optional.ofNullable(mapper.selectByCredentialNo(credentialNo)).map(CredentialMyBatisRepository::toDomain);
    }

    @Override
    public Optional<Credential> findByCredentialHash(String credentialHash) {
        return Optional.ofNullable(mapper.selectByCredentialHash(credentialHash)).map(CredentialMyBatisRepository::toDomain);
    }

    @Override
    public Optional<Credential> findByIdForUpdate(UUID credentialId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(credentialId)).map(CredentialMyBatisRepository::toDomain);
    }

    @Override
    public Optional<Credential> findActiveByGroupId(UUID groupId) {
        return Optional.ofNullable(mapper.selectActiveByGroupId(groupId)).map(CredentialMyBatisRepository::toDomain);
    }

    @Override
    public List<Credential> findByUserId(UUID userId, int limit, int offset, String sort) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        CredentialSort credentialSort = CredentialSort.from(sort);
        return mapper.selectByUserId(userId, limit, offset, credentialSort.field,
                credentialSort.direction)
            .stream()
            .map(CredentialMyBatisRepository::toDomain)
            .toList();
    }

    @Override
    public long countByUserId(UUID userId) {
        return mapper.countByUserId(userId);
    }

    @Override
    public int nextVersionForUpdate(UUID groupId) {
        Integer latestVersion = mapper.selectLatestVersionForUpdate(groupId);
        return latestVersion == null ? 1 : latestVersion + 1;
    }

    @Override
    public void insert(Credential credential) {
        mapper.insert(toRow(credential));
    }

    @Override
    public boolean insertIfChainKeyAvailable(Credential credential) {
        return mapper.insertIfChainKeyAvailable(toRow(credential)) == 1;
    }

    @Override
    public void update(Credential credential) {
        mapper.update(toRow(credential));
    }

    private CredentialRow toRow(Credential credential) {
        return new CredentialRow(
            credential.id(), credential.credentialGroupId(), credential.previousCredentialId(),
            credential.credentialNo(), credential.versionNo(), credential.issuerIdentifier(),
            credential.subjectIdentifier(), credential.credentialType(), credential.status().name(),
            credential.validFrom(), credential.validUntil(), credential.vcPayload(), credential.vcHash(),
            credential.chainKey(), credential.vcHashVersion(), credential.issuedAt(),
            credential.revokedAt(), credential.revocationReason(),
            credential.failureCode(), credential.failureMessage(), credential.createdAt(),
            credential.updatedAt());
    }

    static Credential toDomain(CredentialRow row) {
        return new Credential(
            row.id(), row.credentialGroupId(), row.previousCredentialId(), row.credentialNo(),
            row.versionNo(), row.issuerIdentifier(), row.subjectIdentifier(), row.credentialType(),
            CredentialStatus.valueOf(row.status()), row.validFrom(), row.validUntil(), row.vcPayload(),
            row.vcHash(), row.chainKey(), row.vcHashVersion(), row.issuedAt(), row.revokedAt(),
            row.revocationReason(), row.failureCode(), row.failureMessage(), row.createdAt(),
            row.updatedAt());
    }

    enum CredentialSort {
        CREATED_AT_ASC("createdAt,asc", "created_at", "ASC"),
        CREATED_AT_DESC("createdAt,desc", "created_at", "DESC"),
        ISSUED_AT_ASC("issuedAt,asc", "issued_at", "ASC"),
        ISSUED_AT_DESC("issuedAt,desc", "issued_at", "DESC"),
        UPDATED_AT_ASC("updatedAt,asc", "updated_at", "ASC"),
        UPDATED_AT_DESC("updatedAt,desc", "updated_at", "DESC");

        private final String value;
        final String field;
        final String direction;

        CredentialSort(String value, String field, String direction) {
            this.value = value;
            this.field = field;
            this.direction = direction;
        }

        static CredentialSort from(String value) {
            for (CredentialSort sort : values()) {
                if (sort.value.equals(value)) {
                    return sort;
                }
            }
            throw new IllegalArgumentException("Unsupported credential sort: " + value);
        }
    }
}
