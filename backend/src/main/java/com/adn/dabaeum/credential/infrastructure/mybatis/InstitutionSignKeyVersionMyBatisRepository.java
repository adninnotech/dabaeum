package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InstitutionSignKeyVersionMyBatisRepository implements InstitutionSignKeyVersionRepository {

    private final InstitutionSignKeyVersionMapper mapper;

    public InstitutionSignKeyVersionMyBatisRepository(InstitutionSignKeyVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(InstitutionSignKeyVersion version) {
        mapper.insert(new InstitutionSignKeyVersionRow(version.id(), version.signKeyId(),
            version.institutionId(), version.keyVersion(), version.kidFragment(),
            version.publicKeyJwk(), version.source().name(), version.status().name(),
            version.openbaoCreatedAt(), version.activatedAt(), version.retiredAt(),
            version.compromisedAt(), version.createdBy(), version.createdAt()));
    }

    @Override
    public Optional<InstitutionSignKeyVersion> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersion(UUID signKeyId, int keyVersion) {
        return Optional.ofNullable(mapper.selectBySignKeyIdAndVersion(signKeyId, keyVersion))
            .map(this::toDomain);
    }

    @Override
    public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersionForUpdate(UUID signKeyId, int keyVersion) {
        return Optional.ofNullable(mapper.selectBySignKeyIdAndVersionForUpdate(signKeyId, keyVersion))
            .map(this::toDomain);
    }

    @Override
    public Optional<InstitutionSignKeyVersion> findByInstitutionIdAndKidFragment(
        UUID institutionId, String kidFragment
    ) {
        return Optional.ofNullable(mapper.selectByInstitutionIdAndKidFragment(institutionId, kidFragment))
            .map(this::toDomain);
    }

    @Override
    public List<InstitutionSignKeyVersion> findBySignKeyId(UUID signKeyId) {
        return mapper.selectBySignKeyId(signKeyId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<InstitutionSignKeyVersion> findByInstitutionId(UUID institutionId) {
        return mapper.selectByInstitutionId(institutionId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean retireIfActive(UUID id, Instant retiredAt) {
        return mapper.retireIfActive(id, retiredAt) == 1;
    }

    @Override
    public boolean compromise(UUID id, Instant approvedCompromisedAt) {
        return mapper.compromise(id, approvedCompromisedAt) == 1;
    }

    @Override
    public Optional<SignKeyVersionStatus> findStatusForShare(UUID signKeyId, int keyVersion) {
        return Optional.ofNullable(mapper.selectStatusForShare(signKeyId, keyVersion))
            .map(SignKeyVersionStatus::valueOf);
    }

    private InstitutionSignKeyVersion toDomain(InstitutionSignKeyVersionRow row) {
        return new InstitutionSignKeyVersion(row.id(), row.signKeyId(), row.institutionId(),
            row.keyVersion(), row.kidFragment(), row.publicKeyJwk(),
            InstitutionSignKeyVersion.Source.valueOf(row.source()),
            SignKeyVersionStatus.valueOf(row.status()), row.openbaoCreatedAt(), row.activatedAt(),
            row.retiredAt(), row.compromisedAt(), row.createdBy(), row.createdAt());
    }
}
