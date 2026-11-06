package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InstitutionSignKeyMyBatisRepository implements InstitutionSignKeyRepository {

    private final InstitutionSignKeyMapper mapper;

    public InstitutionSignKeyMyBatisRepository(InstitutionSignKeyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(InstitutionSignKey key) {
        mapper.insert(toRow(key));
    }

    @Override
    public Optional<InstitutionSignKey> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<InstitutionSignKey> findCurrent(UUID institutionId, SignKeyPurpose purpose) {
        return Optional.ofNullable(mapper.selectCurrent(institutionId, purpose.name()))
            .map(this::toDomain);
    }

    @Override
    public List<InstitutionSignKey> findByInstitutionId(UUID institutionId) {
        return mapper.selectByInstitutionId(institutionId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean update(InstitutionSignKey key, int expectedVersionNo) {
        return mapper.update(toRow(key), expectedVersionNo) == 1;
    }

    static InstitutionSignKeyRow toRow(InstitutionSignKey key) {
        return new InstitutionSignKeyRow(key.id(), key.institutionId(), key.purpose().name(),
            key.generation(), key.isCurrent(), key.mount(), key.keyName(), key.keyType(),
            key.currentVersion(), key.status().name(), key.statusReason(), key.rotatedAt(),
            key.suspendedAt(), key.revokedAt(), key.compromisedAt(), key.versionNo(),
            key.createdAt(), key.updatedAt());
    }

    private InstitutionSignKey toDomain(InstitutionSignKeyRow row) {
        return new InstitutionSignKey(row.id(), row.institutionId(),
            SignKeyPurpose.valueOf(row.purpose()), row.generation(), row.isCurrent(), row.mount(),
            row.keyName(), row.keyType(), row.currentVersion(), SignKeyStatus.valueOf(row.status()),
            row.statusReason(), row.rotatedAt(), row.suspendedAt(), row.revokedAt(),
            row.compromisedAt(), row.versionNo(), row.createdAt(), row.updatedAt());
    }
}
